package minesweeper.engine;

import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import javax.swing.SwingUtilities;
import minesweeper.engine.*;
import minesweeper.exceptions.*;
import minesweeper.interfaces.Explodable;
import minesweeper.interfaces.Rewardable;
import minesweeper.model.*;
import minesweeper.model.Board.CellRevealOutcome;
import minesweeper.model.cell.*;
import minesweeper.ui.CellButton;
import minesweeper.ui.ConsoleUI;

/** Dependency-free regression tests for board and engine reveal behavior. */
public final class RevealTests {
    private RevealTests() {}

    public static void main(String[] args) throws Exception {
        List<TestCase> tests = List.of(
                new TestCase("Normal reveal awards one point", RevealTests::normalReveal),
                new TestCase("Mine reveal removes one life", RevealTests::mineReveal),
                new TestCase("Each trap applies its own effect", RevealTests::trapEffects),
                new TestCase("Each bonus applies its own reward", RevealTests::bonusEffects),
                new TestCase("Repeated reveals have no effects", RevealTests::repeatedReveal),
                new TestCase("Flagged cells are protected", RevealTests::flaggedReveal),
                new TestCase("Force reveals clear flags without triggering effects", RevealTests::forceRevealFlags),
                new TestCase("Force-revealed mines display as disabled mines in Swing", RevealTests::forceRevealDisplay),
                new TestCase("Cell views preserve Swing and console rendering", RevealTests::cellViewRendering),
                new TestCase("Flood fill preserves special and flagged cells", RevealTests::floodFill),
                new TestCase("Cell reference overload matches coordinate overload", RevealTests::cellOverload),
                new TestCase("Custom reward cells work without board changes", RevealTests::customReward),
                new TestCase("Custom rewards produce engine messages without casts", RevealTests::customRewardMessage),
                new TestCase("Adjacent counts include custom dangerous cells", RevealTests::customDangerCounts),
                new TestCase("Custom danger prevents an empty-cell flood", RevealTests::customDangerFlood),
                new TestCase("Loss reveals all dangerous cell types without triggering effects", RevealTests::dangerReveal),
                new TestCase("Fatal custom danger reveals other dangerous cells", RevealTests::customDangerLoss),
                new TestCase("Invalid first clicks do not start the game", RevealTests::invalidFirstClick),
                new TestCase("Flagged first clicks do not start the game", RevealTests::flaggedFirstClick),
                new TestCase("First click generation preserves existing flags", RevealTests::firstClickFlags),
                new TestCase("Flagged clicks preserve Freeze", RevealTests::flaggedFreeze),
                new TestCase("Repeated clicks preserve Freeze", RevealTests::repeatedFreeze),
                new TestCase("Invalid clicks preserve Freeze", RevealTests::invalidFreeze),
                new TestCase("Valid reveals consume Freeze once", RevealTests::validFreeze),
                new TestCase("Freeze reports when no random safe cell can be revealed", RevealTests::blockedFreeze),
                new TestCase("Random safe reveals report their actual count and protect other cells", RevealTests::safeRevealCount),
                new TestCase("Nonpositive safe reveal requests leave the board unchanged", RevealTests::nonpositiveSafeReveal),
                new TestCase("Blocked undo preserves its earned token", RevealTests::blockedUndo),
                new TestCase("Undo succeeds once after unflagging a safe cell", RevealTests::successfulUndo),
                new TestCase("Undo without a token leaves an available cell hidden", RevealTests::missingUndo),
                new TestCase("Undo consumes its token before reporting a win", RevealTests::winningUndo),
                new TestCase("Console explains blocked undo without claiming success", () -> undoConsole(true)),
                new TestCase("Console explains a missing undo token", () -> undoConsole(false)),
                new TestCase("Console shows bonus lives without Enable Lives", RevealTests::consoleBonusLives),
                new TestCase("Final safe reveal wins the game", RevealTests::winningReveal),
                new TestCase("Fatal mine reveal loses the game", RevealTests::losingReveal));
        int failures = 0;
        for (TestCase test : tests) {
            try {
                test.body().run();
                System.out.println("PASS: " + test.name());
            } catch (Exception | AssertionError error) {
                failures++;
                System.err.println("FAIL: " + test.name() + " - " + error);
            }
        }
        System.out.println((tests.size() - failures) + "/" + tests.size() + " tests passed.");
        require(failures == 0, "Regression tests failed: " + failures);
    }

    private static Board<Cell> board(Cell... cells) throws InvalidCoordinateException {
        Board<Cell> board = new Board<>(1, cells.length);
        for (int col = 0; col < cells.length; col++) board.setCell(0, col, cells[col]);
        return board;
    }

    private static NormalCell numbered(int col) {
        NormalCell cell = new NormalCell(0, col);
        cell.setAdjacentMines(1);
        return cell;
    }

    private static void normalReveal() throws Exception {
        Board<Cell> board = board(numbered(0));
        board.setTotalSafeCells(1);
        Player player = new Player("Player", 1);
        equal(CellRevealOutcome.NORMAL, board.reveal(0, 0, player));
        equal(1, player.getScore());
        equal(1, board.getRevealedSafeCells());
        require(board.isComplete(), "Last safe cell must complete the board");
    }

    private static void mineReveal() throws Exception {
        MineCell mine = new MineCell(0, 0);
        Board<Cell> board = board(mine);
        Player player = new Player("Player", 2);
        equal(CellRevealOutcome.MINE, board.reveal(0, 0, player));
        equal(1, player.getLives());
        equal(0, board.getRevealedSafeCells());
        require(mine.hasTriggered(), "Mine must be triggered");
    }

    private static void trapEffects() throws Exception {
        for (TrapCell.TrapEffect effect : TrapCell.TrapEffect.values()) {
            TrapCell trap = new TrapCell(0, 0, effect);
            Board<Cell> board = board(trap);
            Player player = new Player("Player", 2);
            equal(CellRevealOutcome.TRAP, board.reveal(0, 0, player));
            require(trap.hasTriggered(), "Trap must be triggered");
            switch (effect) {
                case LOSE_LIFE -> equal(1, player.getLives());
                case REVEAL_RANDOM_MINE -> require(player.hasDebuff("REVEAL_MINE"), "Missing mine debuff");
                case FREEZE_NEXT_MOVE -> require(player.hasDebuff("FREEZE"), "Missing freeze debuff");
            }
            equal(0, board.getRevealedSafeCells());
        }
    }

    private static void bonusEffects() throws Exception {
        for (BonusType type : BonusType.values()) {
            BonusCell bonus = new BonusCell(0, 0, type);
            Board<Cell> board = board(bonus, numbered(1), numbered(2), numbered(3));
            Player player = new Player("Player", 1);
            equal(CellRevealOutcome.BONUS, board.reveal(0, 0, player));
            require(bonus.isCollected(), "Bonus must be collected");
            switch (type) {
                case EXTRA_LIFE -> equal(2, player.getLives());
                case REVEAL_SAFE_CELLS -> equal(4, board.getRevealedSafeCells());
                case UNDO_LAST_MOVE -> require(player.isUndoAvailable(), "Missing undo token");
            }
            equal(type.getScoreValue() + (type == BonusType.REVEAL_SAFE_CELLS ? 3 : 0), player.getScore());
        }
    }

    private static void repeatedReveal() throws Exception {
        Board<Cell> board = board(new MineCell(0, 0), new BonusCell(0, 1, BonusType.EXTRA_LIFE));
        Player player = new Player("Player", 2);
        board.reveal(0, 0, player);
        equal(CellRevealOutcome.ALREADY_REVEALED, board.reveal(0, 0, player));
        equal(1, player.getLives());
        board.reveal(0, 1, player);
        equal(CellRevealOutcome.ALREADY_REVEALED, board.reveal(0, 1, player));
        equal(2, player.getLives());
        equal(10, player.getScore());
        equal(1, board.getRevealedSafeCells());
    }

    private static void flaggedReveal() throws Exception {
        MineCell mine = new MineCell(0, 0);
        mine.flag();
        Board<Cell> board = board(mine);
        Player player = new Player("Player", 1);
        equal(CellRevealOutcome.FLAGGED, board.reveal(0, 0, player));
        require(!mine.isRevealed() && !mine.hasTriggered(), "Flagged mine must remain untouched");
        equal(1, player.getLives());
    }

    private static void floodFill() throws Exception {
        NormalCell first = new NormalCell(0, 0);
        NormalCell second = new NormalCell(0, 1);
        NormalCell flagged = new NormalCell(0, 2);
        flagged.flag();
        BonusCell bonus = new BonusCell(0, 3, BonusType.EXTRA_LIFE);
        Board<Cell> board = board(first, second, flagged, bonus, new MineCell(0, 4));
        Player player = new Player("Player", 1);
        board.reveal(0, 0, player);
        equal(2, board.getRevealedSafeCells());
        equal(2, player.getScore());
        require(!flagged.isRevealed() && !bonus.isCollected() && !board.getCell(0, 4).isRevealed(),
                "Flood fill must protect flags and special cells");
    }

    private static void forceRevealFlags() throws Exception {
        MineCell mine = new MineCell(0, 0);
        mine.flag();
        NormalCell hidden = new NormalCell(0, 1);
        hidden.flag();
        Board<Cell> board = board(mine, hidden);
        equal(2, board.getFlaggedCount());

        mine.forceReveal();
        require(mine.isRevealed() && !mine.isFlagged(), "A force-revealed cell must not keep its flag");
        require(!mine.hasTriggered(), "Force reveal must not trigger the mine");
        equal("*", mine.toString());
        equal(1, board.getFlaggedCount());
        equal(0, board.getRevealedSafeCells());
        require(hidden.isFlagged() && !hidden.isRevealed(), "Other cells must remain unchanged");

        mine.forceReveal();
        mine.flag();
        require(mine.isRevealed() && !mine.isFlagged(), "Repeated force reveal and flag attempts must preserve revealed state");
        require(!mine.hasTriggered(), "Repeated force reveal must remain effect-free");
    }

    private static void forceRevealDisplay() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MineCell mine = new MineCell(0, 0);
            mine.flag();
            CellButton button = new CellButton(0, 0);
            button.updateFromCell(new CellView(mine));
            equal("\uD83D\uDEA9", button.getText());
            require(button.isEnabled(), "A hidden flagged cell must remain enabled");

            mine.forceReveal();
            button.updateFromCell(new CellView(mine));
            equal("\uD83D\uDCA3", button.getText());
            require(!button.isEnabled(), "A revealed mine button must be disabled");
            require(!mine.hasTriggered(), "Rendering must not trigger the mine");
        });
    }

    private static void cellOverload() throws Exception {
        NormalCell cell = numbered(0);
        Board<Cell> board = board(cell);
        equal(CellRevealOutcome.NORMAL, board.reveal(cell, new Player("Player", 1)));
        equal(1, board.getRevealedSafeCells());
    }

    private static void cellViewRendering() throws Exception {
        NormalCell numbered = new NormalCell(0, 3);
        numbered.setAdjacentMines(2);
        List<Cell> cells = List.of(new MineCell(0, 0),
                new TrapCell(0, 1, TrapCell.TrapEffect.LOSE_LIFE),
                new BonusCell(0, 2, BonusType.EXTRA_LIFE), numbered, new CustomRewardCell(0, 4));
        List<String> graphicalSymbols = List.of("\uD83D\uDCA3", "\uD83E\uDEE4", "\uD83C\uDF81", "2", "R");
        ConsoleUI console = new ConsoleUI();
        java.lang.reflect.Method styled = ConsoleUI.class.getDeclaredMethod("styledCell", CellView.class);
        styled.setAccessible(true);
        for (int index = 0; index < cells.size(); index++) {
            Cell cell = cells.get(index);
            cell.forceReveal();
            CellView view = new CellView(cell);
            String expectedGraphical = graphicalSymbols.get(index);
            SwingUtilities.invokeAndWait(() -> {
                CellButton button = new CellButton(view.getRow(), view.getCol());
                button.updateFromCell(view);
                equal(expectedGraphical, button.getText());
                require(!button.isEnabled(), "Revealed cell views must be disabled");
            });
            String text = (String) styled.invoke(console, view);
            equal(cell.getRevealedSymbol(), text.replaceAll("\u001B\\[[;\\d]*m", ""));
        }
    }

    private static void customReward() throws Exception {
        CustomRewardCell cell = new CustomRewardCell(0, 0);
        Board<Cell> board = board(cell);
        board.setTotalSafeCells(1);
        Player player = new Player("Player", 1);
        equal(CellRevealOutcome.BONUS, board.reveal(0, 0, player));
        equal(2, player.getLives());
        require(board.isComplete(), "Custom safe cells must count toward victory");
        equal(CellRevealOutcome.ALREADY_REVEALED, board.reveal(0, 0, player));
        equal(2, player.getLives());
    }

    private static void customDangerCounts() throws Exception {
        Board<Cell> board = new Board<>(2, 2);
        NormalCell normal = new NormalCell(0, 0);
        board.setCell(0, 0, normal);
        board.setCell(0, 1, new MineCell(0, 1));
        board.setCell(1, 0, new TrapCell(1, 0, TrapCell.TrapEffect.LOSE_LIFE));
        board.setCell(1, 1, new CustomDangerCell(1, 1));
        board.calculateAdjacentCounts();
        equal(1, normal.getAdjacentMines());
        equal(1, normal.getAdjacentTraps());
        equal(3, normal.getAdjacentDanger());
        equal("3", normal.getRevealedSymbol());

        board.calculateAdjacentCounts();
        equal(3, normal.getAdjacentDanger());
        board.setCell(1, 1, new BonusCell(1, 1, BonusType.EXTRA_LIFE));
        board.calculateAdjacentCounts();
        equal(2, normal.getAdjacentDanger());
    }

    private static void customDangerFlood() throws Exception {
        NormalCell first = new NormalCell(0, 0);
        NormalCell target = new NormalCell(0, 1);
        CustomDangerCell danger = new CustomDangerCell(0, 2);
        Board<Cell> board = board(first, target, danger);
        board.calculateAdjacentCounts();
        Player player = new Player("Player", 2);
        board.reveal(0, 1, player);
        equal(1, target.getAdjacentDanger());
        require(!first.isRevealed(), "A numbered cell must not start a flood");
        require(!danger.isRevealed() && !danger.hasTriggered(), "Adjacent danger must remain hidden");
        equal(1, board.getRevealedSafeCells());
        equal(1, player.getScore());
        equal(2, player.getLives());
    }

    private static void dangerReveal() throws Exception {
        MineCell mine = new MineCell(0, 0);
        TrapCell trap = new TrapCell(0, 1, TrapCell.TrapEffect.FREEZE_NEXT_MOVE);
        CustomDangerCell flagged = new CustomDangerCell(0, 2);
        flagged.flag();
        CustomDangerCell revealed = new CustomDangerCell(0, 3);
        revealed.forceReveal();
        NormalCell normal = new NormalCell(0, 4);
        BonusCell bonus = new BonusCell(0, 5, BonusType.EXTRA_LIFE);
        Board<Cell> board = board(mine, trap, flagged, revealed, normal, bonus);
        board.revealAllDangerCells();
        board.revealAllDangerCells();
        require(mine.isRevealed() && trap.isRevealed() && flagged.isRevealed() && revealed.isRevealed(),
                "All Explodable cells must be revealed, including flagged cells");
        require(!flagged.isFlagged(), "Danger shown on loss must not retain a flag");
        require(!mine.hasTriggered() && !trap.hasTriggered() && !flagged.hasTriggered() && !revealed.hasTriggered(),
                "Showing dangerous cells must not apply their effects");
        require(!normal.isRevealed() && !bonus.isRevealed() && !bonus.isCollected(),
                "Loss must leave hidden safe and reward cells untouched");
        equal(0, board.getRevealedSafeCells());
    }

    private static void customDangerLoss() throws Exception {
        GameEngine engine = startedEngine();
        Cell target = engine.getBoard().getAllCells().stream()
                .filter(cell -> cell instanceof MineCell).findFirst().orElseThrow();
        CustomDangerCell danger = new CustomDangerCell(target.getRow(), target.getCol());
        engine.getBoard().setCell(target.getRow(), target.getCol(), danger);
        Cell other = engine.getBoard().getAllCells().stream()
                .filter(cell -> cell instanceof MineCell).findFirst().orElseThrow();
        CustomDangerCell flagged = new CustomDangerCell(other.getRow(), other.getCol());
        flagged.flag();
        engine.getBoard().setCell(other.getRow(), other.getCol(), flagged);
        try {
            engine.revealCell(danger.getRow(), danger.getCol());
            throw new AssertionError("Expected a losing GameOverException");
        } catch (GameOverException result) {
            require(!result.isWon(), "Fatal custom danger must lose the game");
            equal(GameState.LOST, engine.getState());
            equal(0, engine.getPlayer().getLives());
            equal(1, engine.getPlayer().getScore());
            equal(1, engine.getBoard().getRevealedSafeCells());
            require(danger.hasTriggered(), "The clicked danger must apply its effect");
            require(flagged.isRevealed() && !flagged.hasTriggered(), "Other danger must only be shown");
            require(!flagged.isFlagged(), "Loss must clear the shown danger's flag");
            require(!engine.getTimer().isRunning(), "Loss must stop the timer");
        }
    }

    private static GameEngine startedEngine() throws Exception {
        GameEngine engine = new GameEngine(GameSettings.beginner(), "Player");
        engine.initialise();
        // Protect other cells so the initial flood cannot complete the board.
        for (Cell cell : engine.getBoard().getAllCells()) {
            if (cell.getRow() != 0 || cell.getCol() != 0) cell.flag();
        }
        engine.revealCell(0, 0);
        return engine;
    }

    private static Cell hiddenNormal(GameEngine engine) {
        return engine.getBoard().getAllCells().stream()
                .filter(cell -> cell instanceof NormalCell && !cell.isRevealed()).findFirst().orElseThrow();
    }

    private static void customRewardMessage() throws Exception {
        GameEngine engine = startedEngine();
        Cell target = hiddenNormal(engine);
        CustomRewardCell custom = new CustomRewardCell(target.getRow(), target.getCol());
        engine.getBoard().setCell(target.getRow(), target.getCol(), custom);
        RevealResult result = engine.revealCell(target.getRow(), target.getCol());
        equal(RevealResult.Outcome.BONUS, result.getOutcome());
        require(result.getMessage().contains("Custom reward"), "Engine must use the cell's description");
        equal(2, engine.getPlayer().getLives());
    }

    private static void invalidFirstClick() throws Exception {
        GameEngine engine = new GameEngine(GameSettings.beginner(), "Player");
        engine.initialise();
        expectInvalidCoordinate(engine);
        equal(GameState.NOT_STARTED, engine.getState());
        require(!engine.getTimer().isRunning(), "Invalid input must not start the timer");
    }

    private static void flaggedFirstClick() throws Exception {
        GameEngine engine = new GameEngine(GameSettings.beginner(), "Player");
        engine.initialise();
        engine.toggleFlag(0, 0);
        equal(RevealResult.Outcome.FLAGGED, engine.revealCell(0, 0).getOutcome());
        equal(GameState.NOT_STARTED, engine.getState());
    }

    private static void firstClickFlags() throws Exception {
        GameEngine engine = startedEngine();
        equal(80, engine.getBoard().getFlaggedCount());
        equal(10L, engine.getBoard().getAllCells().stream().filter(cell -> cell instanceof MineCell).count());
        for (int[] neighbour : engine.getBoard().getNeighbourCoords(0, 0)) {
            require(engine.getBoard().getCell(neighbour[0], neighbour[1]) instanceof NormalCell,
                    "First-click neighbourhood must be safe");
        }
    }

    private static void flaggedFreeze() throws Exception {
        GameEngine engine = startedEngine();
        Cell cell = hiddenNormal(engine);
        engine.getPlayer().addDebuff("FREEZE");
        equal(RevealResult.Outcome.FLAGGED, engine.revealCell(cell.getRow(), cell.getCol()).getOutcome());
        require(engine.getPlayer().hasDebuff("FREEZE"), "Flagged click must preserve Freeze");
        equal(1, engine.getBoard().getRevealedSafeCells());
    }

    private static void repeatedFreeze() throws Exception {
        GameEngine engine = startedEngine();
        engine.getPlayer().addDebuff("FREEZE");
        equal(RevealResult.Outcome.ALREADY_REVEALED, engine.revealCell(0, 0).getOutcome());
        require(engine.getPlayer().hasDebuff("FREEZE"), "Repeated click must preserve Freeze");
        equal(1, engine.getBoard().getRevealedSafeCells());
    }

    private static void invalidFreeze() throws Exception {
        GameEngine engine = startedEngine();
        engine.getPlayer().addDebuff("FREEZE");
        expectInvalidCoordinate(engine);
        require(engine.getPlayer().hasDebuff("FREEZE"), "Invalid click must preserve Freeze");
    }

    private static void validFreeze() throws Exception {
        GameEngine engine = startedEngine();
        Cell cell = hiddenNormal(engine);
        engine.toggleFlag(cell.getRow(), cell.getCol());
        engine.getPlayer().addDebuff("FREEZE");
        equal(RevealResult.Outcome.FROZEN, engine.revealCell(cell.getRow(), cell.getCol()).getOutcome());
        require(!engine.getPlayer().hasDebuff("FREEZE"), "Valid reveal must consume Freeze");
        equal(2, engine.getBoard().getRevealedSafeCells());
    }

    private static void blockedFreeze() throws Exception {
        GameEngine engine = startedEngine();
        BonusCell bonus = new BonusCell(0, 1, BonusType.EXTRA_LIFE);
        engine.getBoard().setCell(0, 1, bonus);
        engine.getPlayer().addDebuff("FREEZE");
        int score = engine.getScore();
        RevealResult result = engine.revealCell(0, 1);
        equal(RevealResult.Outcome.FROZEN, result.getOutcome());
        require(result.getMessage().contains("no cell was revealed"), "Freeze must not claim a nonexistent reveal");
        require(!bonus.isRevealed() && !bonus.isCollected(), "The skipped target must stay hidden");
        require(!engine.getPlayer().hasDebuff("FREEZE"), "Freeze still consumes one valid attempt");
        equal(score, engine.getScore());
        equal(1, engine.getBoard().getRevealedSafeCells());
        equal(RevealResult.Outcome.BONUS, engine.revealCell(0, 1).getOutcome());
        require(bonus.isCollected(), "The next attempt must reveal the original bonus normally");
    }

    private static void expectInvalidCoordinate(GameEngine engine) throws Exception {
        try {
            engine.revealCell(-1, 0);
            throw new AssertionError("Expected InvalidCoordinateException");
        } catch (InvalidCoordinateException expected) {
            // Expected validation failure.
        }
    }

    private static GameEngine engineWithUndoToken() throws Exception {
        GameEngine engine = startedEngine();
        // Replace a safe placeholder with a real bonus; keep the safe-cell total unchanged.
        engine.getBoard().setCell(0, 1, new BonusCell(0, 1, BonusType.UNDO_LAST_MOVE));
        engine.revealCell(0, 1);
        require(engine.isUndoAvailable(), "Revealing the bonus must grant a token");
        return engine;
    }

    private static void safeRevealCount() throws Exception {
        NormalCell flagged = numbered(2);
        flagged.flag();
        MineCell mine = new MineCell(0, 3);
        TrapCell trap = new TrapCell(0, 4, TrapCell.TrapEffect.LOSE_LIFE);
        BonusCell bonus = new BonusCell(0, 5, BonusType.UNDO_LAST_MOVE);
        Board<Cell> board = board(numbered(0), numbered(1), flagged, mine, trap, bonus);
        Player player = new Player("Player", 1);
        equal(1, board.revealRandomSafeCells(1, player));
        equal(1, board.getRevealedSafeCells());
        equal(1, board.revealRandomSafeCells(100, player));
        equal(0, board.revealRandomSafeCells(1, player));
        equal(2, board.getRevealedSafeCells());
        equal(2, player.getScore());
        require(flagged.isFlagged() && !flagged.isRevealed(), "Random reveals must respect flags");
        require(!mine.isRevealed() && !trap.isRevealed() && !bonus.isRevealed(),
                "Random reveals must leave mines, traps and bonuses untouched");
        require(!mine.hasTriggered() && !trap.hasTriggered() && !bonus.isCollected(),
                "Random reveals must not trigger special effects");
    }

    private static void nonpositiveSafeReveal() throws Exception {
        NormalCell cell = numbered(0);
        Board<Cell> board = board(cell);
        Player player = new Player("Player", 1);
        equal(0, board.revealRandomSafeCells(0, player));
        equal(0, board.revealRandomSafeCells(-1, player));
        require(!cell.isRevealed(), "A nonpositive count must leave cells hidden");
        equal(0, board.getRevealedSafeCells());
        equal(0, player.getScore());
    }

    private static void blockedUndo() throws Exception {
        GameEngine engine = engineWithUndoToken();
        int score = engine.getScore();
        int flags = engine.getBoard().getFlaggedCount();
        require(!engine.useUndo(), "Undo must fail when every hidden normal cell is flagged");
        require(engine.isUndoAvailable(), "An unsuccessful undo must preserve the token");
        require(!engine.useUndo() && engine.isUndoAvailable(), "Repeated blocked attempts must preserve the token");
        equal(score, engine.getScore());
        equal(flags, engine.getBoard().getFlaggedCount());
        equal(2, engine.getBoard().getRevealedSafeCells());
        equal(GameState.IN_PROGRESS, engine.getState());
    }

    private static void successfulUndo() throws Exception {
        GameEngine engine = engineWithUndoToken();
        require(!engine.useUndo() && engine.isUndoAvailable(), "Blocked undo must allow a later retry");
        Cell target = hiddenNormal(engine);
        engine.toggleFlag(target.getRow(), target.getCol());
        int score = engine.getScore();
        require(engine.useUndo(), "Undo must reveal an available safe cell");
        require(target.isRevealed() && !engine.isUndoAvailable(), "A successful undo must consume the token");
        equal(score + 1, engine.getScore());
        equal(3, engine.getBoard().getRevealedSafeCells());
        require(!engine.useUndo(), "The consumed token cannot be reused");
        equal(score + 1, engine.getScore());
    }

    private static void missingUndo() throws Exception {
        GameEngine engine = startedEngine();
        Cell target = hiddenNormal(engine);
        engine.toggleFlag(target.getRow(), target.getCol());
        require(!engine.useUndo(), "Undo must require a token");
        require(!target.isRevealed() && !engine.isUndoAvailable(), "Missing token must leave the cell unchanged");
        equal(1, engine.getScore());
        equal(1, engine.getBoard().getRevealedSafeCells());
    }

    private static void winningUndo() throws Exception {
        GameEngine engine = engineWithUndoToken();
        Cell target = hiddenNormal(engine);
        // Keep the final target flagged while revealing every other safe cell through the engine.
        for (Cell cell : engine.getBoard().getAllCells()) {
            if (cell instanceof NormalCell && !cell.isRevealed() && cell != target) {
                engine.toggleFlag(cell.getRow(), cell.getCol());
                engine.revealCell(cell.getRow(), cell.getCol());
            }
        }
        equal(engine.getBoard().getTotalSafeCells() - 1, engine.getBoard().getRevealedSafeCells());
        engine.toggleFlag(target.getRow(), target.getCol());
        int score = engine.getScore();
        try {
            engine.useUndo();
            throw new AssertionError("Expected a winning GameOverException");
        } catch (GameOverException result) {
            require(result.isWon(), "The last safe reveal must win");
            require(target.isRevealed() && !engine.isUndoAvailable(), "A winning undo must consume the token");
            equal(GameState.WON, engine.getState());
            equal(score + 101, engine.getScore());
            require(!engine.isTimerRunning(), "A win must stop the timer");
        }
    }

    private static void undoConsole(boolean hasToken) throws Exception {
        GameEngine engine = hasToken ? engineWithUndoToken() : startedEngine();
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            ConsoleUI console = new ConsoleUI();
            java.lang.reflect.Field engineField = ConsoleUI.class.getDeclaredField("engine");
            engineField.setAccessible(true);
            engineField.set(console, engine);
            java.lang.reflect.Method undo = ConsoleUI.class.getDeclaredMethod("handleUndo");
            undo.setAccessible(true);
            equal(Boolean.FALSE, undo.invoke(console));
        } finally {
            System.setOut(originalOutput);
        }
        String message = output.toString(StandardCharsets.UTF_8);
        String expected = hasToken ? "No unflagged safe cell available" : "No undo token available";
        require(message.contains(expected), "Console must explain why undo was not used");
        require(!message.contains("Undo used!"), "Console must not claim an unsuccessful reveal");
        equal(hasToken, engine.isUndoAvailable());
    }

    private static void consoleBonusLives() throws Exception {
        GameEngine engine = new GameEngine(new GameSettings.Builder()
                .bonusesEnabled(true).bonusCount(1).build(), "Player");
        engine.initialise();
        for (Cell cell : engine.getBoard().getAllCells()) {
            if (cell.getRow() != 0 || cell.getCol() != 0) engine.toggleFlag(cell.getRow(), cell.getCol());
        }
        engine.revealCell(0, 0);
        engine.getBoard().setCell(0, 1, new BonusCell(0, 1, BonusType.EXTRA_LIFE));
        engine.revealCell(0, 1);
        equal(2, engine.getLives());
        ConsoleUI console = new ConsoleUI();
        java.lang.reflect.Field engineField = ConsoleUI.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        engineField.set(console, engine);
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            java.lang.reflect.Method status = ConsoleUI.class.getDeclaredMethod("printStatusBar");
            status.setAccessible(true);
            status.invoke(console);
            require(output.toString(StandardCharsets.UTF_8).contains("❤ 2"),
                    "The status bar must show lives earned from bonuses");
            for (Cell cell : engine.getBoard().getAllCells()) {
                if (!(cell instanceof Explodable) && !cell.isRevealed()) {
                    engine.toggleFlag(cell.getRow(), cell.getCol());
                    try { engine.revealCell(cell.getRow(), cell.getCol()); }
                    catch (GameOverException win) { require(win.isWon(), "Safe cells must win"); }
                }
            }
            equal(GameState.WON, engine.getState());
            java.lang.reflect.Method summary = ConsoleUI.class.getDeclaredMethod("printWinSummary");
            summary.setAccessible(true);
            summary.invoke(console);
            require(output.toString(StandardCharsets.UTF_8).contains("Lives left  : " + engine.getLives()),
                    "The victory summary must include bonus lives");
        } finally {
            System.setOut(originalOutput);
        }
    }

    private static void winningReveal() throws Exception {
        GameEngine engine = startedEngine();
        try {
            for (Cell cell : engine.getBoard().getAllCells()) {
                if (cell instanceof NormalCell && !cell.isRevealed()) {
                    engine.toggleFlag(cell.getRow(), cell.getCol());
                    engine.revealCell(cell.getRow(), cell.getCol());
                }
            }
            throw new AssertionError("Expected a winning GameOverException");
        } catch (GameOverException result) {
            require(result.isWon(), "All safe reveals must win");
            equal(GameState.WON, engine.getState());
            require(engine.getBoard().isComplete(), "Winning board must be complete");
            equal(171, engine.getPlayer().getScore());
        }
    }

    private static void losingReveal() throws Exception {
        GameEngine engine = startedEngine();
        Cell mine = engine.getBoard().getAllCells().stream()
                .filter(cell -> cell instanceof MineCell).findFirst().orElseThrow();
        engine.toggleFlag(mine.getRow(), mine.getCol());
        try {
            engine.revealCell(mine.getRow(), mine.getCol());
            throw new AssertionError("Expected a losing GameOverException");
        } catch (GameOverException result) {
            require(!result.isWon(), "A fatal mine must lose");
            equal(GameState.LOST, engine.getState());
            equal(0, engine.getPlayer().getLives());
            require(!engine.getTimer().isRunning(), "Loss must stop the timer");
        }
    }

    private static void equal(Object expected, Object actual) {
        require(expected.equals(actual), "Expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record TestCase(String name, TestBody body) {}

    @FunctionalInterface
    private interface TestBody {
        void run() throws Exception;
    }

    private static final class CustomDangerCell extends Cell implements Explodable {
        private boolean triggered;

        CustomDangerCell(int row, int col) { super(row, col); }

        @Override
        protected CellRevealOutcome onReveal(Player player, Board<? extends Cell> board) {
            explode(player);
            return CellRevealOutcome.MINE;
        }

        @Override
        public void explode(Player player) {
            triggered = true;
            player.loseLife();
        }

        @Override
        public boolean hasTriggered() { return triggered; }

        @Override
        public String getRevealedSymbol() { return "D"; }

        @Override
        public String getType() { return "DANGER"; }
    }

    private static final class CustomRewardCell extends Cell implements Rewardable {
        CustomRewardCell(int row, int col) { super(row, col); }

        @Override
        protected CellRevealOutcome onReveal(Player player, Board<? extends Cell> board) {
            applyReward(player, board);
            return CellRevealOutcome.BONUS;
        }

        @Override
        public String getRevealDescription() { return getRewardDescription(); }

        @Override
        public void applyReward(Player player, Board<? extends Cell> board) { player.gainLife(); }

        @Override
        public String getRewardDescription() { return "Custom reward"; }

        @Override
        public String getRevealedSymbol() { return "R"; }

        @Override
        public String getType() { return "REWARD"; }
    }
}
