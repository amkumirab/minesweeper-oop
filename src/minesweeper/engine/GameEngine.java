package minesweeper.engine;

import minesweeper.exceptions.GameOverException;
import minesweeper.exceptions.InvalidCoordinateException;
import minesweeper.model.Board;
import minesweeper.model.BonusType;
import minesweeper.model.Player;
import minesweeper.model.cell.*;

import java.util.*;

/**
 * Central orchestrator of the game.
 *
 * Responsibilities (single responsibility per sub-method):
 *  - Board initialisation (lazy: generated on first click for a safe start)
 *  - Processing player actions: reveal, flag, undo
 *  - Evaluating win / loss conditions
 *  - Delegating time management to GameTimer
 *
 * Composition: GameEngine owns Board, Player, GameSettings, and GameTimer.
 * It does NOT extend any of them — it coordinates them.
 *
 * Polymorphism: uses Board.CellRevealOutcome (enum) to dispatch post-reveal logic
 * without instanceof chains in the main flow.
 */
public class GameEngine {

    // ── dependencies (composition) ───────────────────────────────────────────
    private       Board<Cell>    board;
    private final Player         player;
    private final GameSettings   settings;
    private       GameState      state;
    private       GameTimer      timer;

    private final Random random;

    // ── constructor ──────────────────────────────────────────────────────────

    public GameEngine(GameSettings settings, String playerName) {
        this.settings = settings;
        this.player   = new Player(
                playerName,
                settings.isLivesEnabled() ? settings.getMaxLives() : 1);
        this.state    = GameState.NOT_STARTED;
        this.random   = new Random();
    }

    // ── initialisation ───────────────────────────────────────────────────────

    /**
     * Creates an empty board and prepares the timer (if enabled).
     * Mines are NOT placed yet — that happens on the first reveal (safe-start guarantee).
     */
    public void initialise() {
        int rows = settings.getRows();
        int cols = settings.getCols();
        board = new Board<>(rows, cols);

        // Pre-fill every cell with a hidden NormalCell placeholder so the board
        // can be displayed before the first reveal (cells are replaced on first click).
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                placeCell(r, c, new NormalCell(r, c));
            }
        }

        timer = new GameTimer(settings.isTimeLimitEnabled()
                ? settings.getTimeLimitSeconds()
                : 0);
        state = GameState.NOT_STARTED;
    }

    // ── player actions ───────────────────────────────────────────────────────

    /**
     * Main action: reveal the cell at (row, col).
     *
     * On first call the board is populated (safe-start: first click area is mine-free).
     * Handles debuffs, timer check, and win/loss detection.
     *
     * @throws InvalidCoordinateException if (row, col) is out of bounds
     * @throws GameOverException          if this action ends the game
     */
    public RevealResult revealCell(int row, int col)
            throws InvalidCoordinateException, GameOverException {

        // Validate before generating the board or consuming a pending debuff.
        Cell requestedCell = board.getCell(row, col);

        if (state == GameState.NOT_STARTED && requestedCell.isFlagged()) {
            return new RevealResult(RevealResult.Outcome.FLAGGED, row, col,
                    "Cell is flagged. Unflag it first (use 'flag "
                            + (row + 1) + " " + (col + 1) + "').");
        }

        // ── first move: generate the board ──────────────────────────────────
        if (state == GameState.NOT_STARTED) {
            generateBoard(row, col);
            state = GameState.IN_PROGRESS;
            if (timer != null) timer.start();
        }

        assertGameInProgress();
        checkTimer();

        // ── FREEZE debuff: skip this reveal, auto-reveal a safe cell instead ─
        if (player.hasDebuff("FREEZE")) {
            player.removeDebuff("FREEZE");
            board.revealRandomSafeCells(1, player);
            checkWin();
            return new RevealResult(RevealResult.Outcome.FROZEN, row, col,
                    "❄ You were frozen! A random safe cell was revealed instead.");
        }

        // ── normal reveal ────────────────────────────────────────────────────
        Board.CellRevealOutcome outcome = board.reveal(row, col, player);

        return switch (outcome) {
            case ALREADY_REVEALED ->
                    new RevealResult(RevealResult.Outcome.ALREADY_REVEALED, row, col,
                            "That cell is already revealed.");

            case FLAGGED ->
                    new RevealResult(RevealResult.Outcome.FLAGGED, row, col,
                            "Cell is flagged. Unflag it first (use 'flag " + (row + 1) + " " + (col + 1) + "').");

            case MINE -> {
                player.recordMove(row, col);
                yield handleMineHit(row, col);
            }

            case TRAP -> {
                player.recordMove(row, col);
                yield handleTrapHit(row, col);
            }

            case BONUS -> {
                player.recordMove(row, col);
                checkWin();
                Cell bc = board.getCell(row, col);
                yield new RevealResult(RevealResult.Outcome.BONUS, row, col,
                        "🎁 Bonus! " + ((BonusCell) bc).getRewardDescription());
            }

            default -> {   // NORMAL / UNKNOWN
                player.recordMove(row, col);
                checkWin();
                yield new RevealResult(RevealResult.Outcome.NORMAL, row, col,
                        "Cell revealed.");
            }
        };
    }

    /**
     * Toggles a flag on/off at (row, col).
     *
     * @throws InvalidCoordinateException if out of bounds
     */
    public void toggleFlag(int row, int col) throws InvalidCoordinateException {
        if (state != GameState.IN_PROGRESS && state != GameState.NOT_STARTED) return;
        board.toggleFlag(row, col);
    }

    /**
     * Attempts to use the undo token granted by a BonusCell.
     * Effect: reveals one random safe cell on behalf of the player.
     *
     * @return true if undo was available and consumed, false otherwise
     * @throws GameOverException if the safe reveal triggers a win
     */
    public boolean useUndo() throws GameOverException {
        assertGameInProgress();
        checkTimer();
        if (!player.isUndoAvailable()) return false;
        player.consumeUndo();
        board.revealRandomSafeCells(1, player);
        checkWin();
        return true;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private RevealResult handleMineHit(int row, int col) throws GameOverException {
        if (!player.isAlive()) {
            endGame(false);
            throw new GameOverException(false);
        }
        // Lives absorbed the hit
        return new RevealResult(RevealResult.Outcome.MINE, row, col,
                "💣 Mine! You lost a life. Lives remaining: " + player.getLives());
    }

    private RevealResult handleTrapHit(int row, int col)
            throws InvalidCoordinateException, GameOverException {

        // REVEAL_MINE debuff: expose one random hidden mine on the board
        if (player.hasDebuff("REVEAL_MINE")) {
            player.removeDebuff("REVEAL_MINE");
            exposeRandomMine();
        }

        if (!player.isAlive()) {
            endGame(false);
            throw new GameOverException(false);
        }

        Cell cell = board.getCell(row, col);
        String effectDesc = (cell instanceof TrapCell tc)
                ? tc.getEffect().getDescription()
                : "Unknown trap effect";

        return new RevealResult(RevealResult.Outcome.TRAP, row, col,
                "🪤 Trap! " + effectDesc
                + (player.hasDebuff("FREEZE") ? " (next move frozen)" : "")
                + " | Lives: " + player.getLives());
    }

    private void exposeRandomMine() {
        List<Cell> hidden = new ArrayList<>();
        for (Cell c : board.getAllCells()) {
            if (c instanceof MineCell && !c.isRevealed() && !c.isFlagged()) {
                hidden.add(c);
            }
        }
        if (!hidden.isEmpty()) {
            hidden.get(random.nextInt(hidden.size())).forceReveal();
        }
    }

    private void checkWin() throws GameOverException {
        if (board.isComplete()) {
            endGame(true);
            throw new GameOverException(true);
        }
    }

    private void endGame(boolean won) {
        state = won ? GameState.WON : GameState.LOST;
        if (timer != null) timer.stop();
        if (!won) board.revealAllDangerCells();
        if (won)  player.addScore(100);  // win bonus
    }

    private void assertGameInProgress() throws GameOverException {
        if (state == GameState.WON)  throw new GameOverException(true);
        if (state == GameState.LOST) throw new GameOverException(false);
    }

    private void checkTimer() throws GameOverException {
        if (timer != null && timer.isTimeUp()) {
            endGame(false);
            throw new GameOverException(false);
        }
    }

    // ── board generation ─────────────────────────────────────────────────────

    /**
     * Populates the board with mines, traps, bonuses, and normal cells.
     * The 3×3 area around (firstRow, firstCol) is guaranteed mine/trap-free.
     */
    private void generateBoard(int firstRow, int firstCol) {
        int rows      = settings.getRows();
        int cols      = settings.getCols();
        int totalCells = rows * cols;

        // Build safe zone around first click
        Set<Integer> safeZone = new HashSet<>();
        safeZone.add(firstRow * cols + firstCol);
        for (int[] nb : board.getNeighbourCoords(firstRow, firstCol)) {
            safeZone.add(nb[0] * cols + nb[1]);
        }

        // Pool of positions available for danger placement
        List<Integer> available = new ArrayList<>(totalCells);
        for (int i = 0; i < totalCells; i++) {
            if (!safeZone.contains(i)) available.add(i);
        }
        Collections.shuffle(available, random);

        int idx         = 0;
        int safeCells   = 0;
        Set<Integer> specialPositions = new HashSet<>();

        int mines   = settings.getMineCount();
        int traps   = settings.isTrapsEnabled()   ? settings.getTrapCount()   : 0;
        int bonuses = settings.isBonusesEnabled()  ? settings.getBonusCount()  : 0;

        TrapCell.TrapEffect[] trapEffects = TrapCell.TrapEffect.values();
        BonusType[]           bonusTypes  = BonusType.values();

        // ── place mines ──────────────────────────────────────────────────────
        for (int i = 0; i < mines && idx < available.size(); i++, idx++) {
            int pos = available.get(idx);
            int r = pos / cols, c = pos % cols;
            placeCell(r, c, new MineCell(r, c));
            specialPositions.add(pos);
        }

        // ── place traps ──────────────────────────────────────────────────────
        for (int i = 0; i < traps && idx < available.size(); i++, idx++) {
            int pos = available.get(idx);
            int r = pos / cols, c = pos % cols;
            TrapCell.TrapEffect effect = trapEffects[random.nextInt(trapEffects.length)];
            placeCell(r, c, new TrapCell(r, c, effect));
            specialPositions.add(pos);
        }

        // ── place bonuses ─────────────────────────────────────────────────────
        for (int i = 0; i < bonuses && idx < available.size(); i++, idx++) {
            int pos = available.get(idx);
            int r = pos / cols, c = pos % cols;
            BonusType bt = bonusTypes[random.nextInt(bonusTypes.length)];
            placeCell(r, c, new BonusCell(r, c, bt));
            specialPositions.add(pos);
            safeCells++;   // bonuses count as safe (win condition)
        }

        // ── fill remaining with NormalCells ──────────────────────────────────
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int pos = r * cols + c;
                if (!specialPositions.contains(pos)) {
                    placeCell(r, c, new NormalCell(r, c));
                    safeCells++;
                }
            }
        }

        board.setTotalSafeCells(safeCells);
        board.calculateAdjacentCounts();
    }

    private void placeCell(int r, int c, Cell cell) {
        try {
            Cell previousCell = board.getCell(r, c);
            if (previousCell != null && previousCell.isFlagged()) {
                cell.flag();
            }
            board.setCell(r, c, cell);
        } catch (InvalidCoordinateException e) {
            throw new RuntimeException("Unexpected coordinate error during board generation", e);
        }
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public Board<Cell>  getBoard()    { return board;    }
    public Player       getPlayer()   { return player;   }
    public GameSettings getSettings() { return settings; }
    public GameState    getState()    { return state;    }
    public GameTimer    getTimer()    { return timer;    }

    /** Mines still unaccounted for (total mines minus flagged cells). */
    public int getRemainingMineCount() {
        return settings.getMineCount() - board.getFlaggedCount();
    }
}
