package minesweeper;

import java.util.List;
import minesweeper.engine.*;
import minesweeper.exceptions.*;
import minesweeper.interfaces.Explodable;
import minesweeper.interfaces.Rewardable;
import minesweeper.model.*;
import minesweeper.model.Board.CellRevealOutcome;
import minesweeper.model.cell.*;

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

    private static void cellOverload() throws Exception {
        NormalCell cell = numbered(0);
        Board<Cell> board = board(cell);
        equal(CellRevealOutcome.NORMAL, board.reveal(cell, new Player("Player", 1)));
        equal(1, board.getRevealedSafeCells());
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

    private static void expectInvalidCoordinate(GameEngine engine) throws Exception {
        try {
            engine.revealCell(-1, 0);
            throw new AssertionError("Expected InvalidCoordinateException");
        } catch (InvalidCoordinateException expected) {
            // Expected validation failure.
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
