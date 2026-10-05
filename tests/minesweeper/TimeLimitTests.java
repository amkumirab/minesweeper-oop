package minesweeper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import minesweeper.engine.GameEngine;
import minesweeper.engine.GameSettings;
import minesweeper.engine.GameState;
import minesweeper.engine.GameTimer;
import minesweeper.exceptions.GameOverException;
import minesweeper.interfaces.Explodable;
import minesweeper.model.cell.Cell;
import minesweeper.model.cell.NormalCell;
import minesweeper.ui.ConsoleUI;

/** Dependency-free regression tests for time-limit enforcement. */
public final class TimeLimitTests {
    private TimeLimitTests() {}

    public static void main(String[] args) throws Exception {
        List<TestCase> tests = List.of(
                new TestCase("An expired game rejects flag changes", TimeLimitTests::expiredFlag),
                new TestCase("Timeout happens before revealing or consuming Freeze", TimeLimitTests::expiredReveal),
                new TestCase("Timeout leaves an unused undo token intact", TimeLimitTests::expiredUndo),
                new TestCase("Invalid coordinates cannot postpone timeout", TimeLimitTests::expiredInvalidCoordinates),
                new TestCase("Polling expires the game without performing a move", TimeLimitTests::expiredPoll),
                new TestCase("Polling does not start an unstarted game", TimeLimitTests::unstartedPoll),
                new TestCase("Polling leaves an unexpired game unchanged", TimeLimitTests::unexpiredPoll),
                new TestCase("Unlimited games do not expire", TimeLimitTests::unlimitedPoll),
                new TestCase("Polling preserves finished games", TimeLimitTests::finishedPoll),
                new TestCase("Console checks timeout before help", () -> expiredConsole("help")),
                new TestCase("Console checks timeout after blank input", () -> expiredConsole("")),
                new TestCase("Console checks timeout before flag input", () -> expiredConsole("flag 1 2")),
                new TestCase("Console checks timeout before undo input", () -> expiredConsole("undo")));
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
        System.out.println((tests.size() - failures) + "/" + tests.size() + " time-limit tests passed.");
        require(failures == 0, "Time-limit regression tests failed: " + failures);
    }

    private static GameSettings timedSettings() throws Exception {
        return new GameSettings.Builder().rows(9).cols(9).mineCount(10)
                .timeLimitEnabled(true).timeLimitSeconds(30).build();
    }

    private static GameEngine startedEngine(GameSettings settings) throws Exception {
        GameEngine engine = new GameEngine(settings, "Player");
        engine.initialise();
        // Keep the first reveal from winning through flood fill.
        for (Cell cell : engine.getBoard().getAllCells()) {
            if (cell.getRow() != 0 || cell.getCol() != 0) cell.flag();
        }
        engine.revealCell(0, 0);
        return engine;
    }

    private static void expire(GameEngine engine) throws ReflectiveOperationException {
        // Advance elapsed time without sleeping or changing the production clock API.
        Field start = GameTimer.class.getDeclaredField("startEpochMs");
        start.setAccessible(true);
        start.setLong(engine.getTimer(), System.currentTimeMillis() - 60_000L);
    }

    private static void expiredFlag() throws Exception {
        GameEngine engine = startedEngine(timedSettings());
        Cell target = engine.getBoard().getCell(0, 1);
        expire(engine);
        expectLoss(() -> engine.toggleFlag(0, 1));
        require(target.isFlagged(), "Timeout must leave the flag unchanged");
        requireLost(engine);
    }

    private static void expiredReveal() throws Exception {
        GameEngine engine = startedEngine(timedSettings());
        engine.toggleFlag(0, 1);
        engine.getPlayer().addDebuff("FREEZE");
        expire(engine);
        expectLoss(() -> engine.revealCell(0, 1));
        require(!engine.getBoard().getCell(0, 1).isRevealed(), "Timeout must not reveal the safe target");
        require(engine.getPlayer().hasDebuff("FREEZE"), "Timeout must not consume Freeze");
        require(engine.getBoard().getRevealedSafeCells() == 1, "Timeout must not increase safe reveals");
        requireLost(engine);
    }

    private static void expiredUndo() throws Exception {
        GameEngine engine = startedEngine(timedSettings());
        engine.getPlayer().setUndoAvailable(true);
        expire(engine);
        expectLoss(() -> engine.useUndo());
        require(engine.getPlayer().isUndoAvailable(), "Timeout must not consume the token");
        requireLost(engine);
    }

    private static void expiredInvalidCoordinates() throws Exception {
        GameEngine revealEngine = startedEngine(timedSettings());
        expire(revealEngine);
        expectLoss(() -> revealEngine.revealCell(-1, 0));
        requireLost(revealEngine);

        GameEngine flagEngine = startedEngine(timedSettings());
        expire(flagEngine);
        expectLoss(() -> flagEngine.toggleFlag(-1, 0));
        requireLost(flagEngine);
    }

    private static void expiredPoll() throws Exception {
        GameEngine engine = startedEngine(timedSettings());
        int score = engine.getPlayer().getScore();
        int lives = engine.getPlayer().getLives();
        expire(engine);
        expectLoss(engine::checkTimeLimit);
        require(engine.getPlayer().getScore() == score, "Polling must not change the score");
        require(engine.getPlayer().getLives() == lives, "Polling must not apply cell damage");
        require(engine.getBoard().getRevealedSafeCells() == 1, "Polling must not reveal safe cells");
        int[] lastMove = engine.getPlayer().peekLastMove();
        require(lastMove[0] == 0 && lastMove[1] == 0, "Polling must not record a move");
        for (Cell cell : engine.getBoard().getAllCells()) {
            if (cell instanceof Explodable) require(cell.isRevealed(), "Loss must expose danger cells");
        }
        requireLost(engine);
    }

    private static void unstartedPoll() throws Exception {
        GameEngine engine = new GameEngine(timedSettings(), "Player");
        engine.checkTimeLimit();
        engine.initialise();
        engine.toggleFlag(0, 0);
        engine.checkTimeLimit();
        require(engine.getState() == GameState.NOT_STARTED, "Polling must not start the game");
        require(!engine.getTimer().isRunning(), "Polling must not start the timer");
        require(engine.getBoard().getCell(0, 0).isFlagged(), "Polling must preserve pre-game flags");
    }

    private static void unexpiredPoll() throws Exception {
        GameEngine engine = startedEngine(timedSettings());
        engine.checkTimeLimit();
        require(engine.getState() == GameState.IN_PROGRESS, "An unexpired game must continue");
        require(engine.getBoard().getRevealedSafeCells() == 1, "Polling must not perform a move");
        engine.toggleFlag(0, 1);
        require(!engine.getBoard().getCell(0, 1).isFlagged(), "Valid flags must still toggle");
    }

    private static void unlimitedPoll() throws Exception {
        GameEngine engine = startedEngine(GameSettings.beginner());
        expire(engine);
        engine.checkTimeLimit();
        engine.toggleFlag(0, 1);
        require(engine.getState() == GameState.IN_PROGRESS, "Unlimited games must stay active");
        require(!engine.getBoard().getCell(0, 1).isFlagged(), "Unlimited games must allow flag changes");
    }

    private static void finishedPoll() throws Exception {
        GameEngine lost = startedEngine(timedSettings());
        expire(lost);
        expectLoss(lost::checkTimeLimit);
        lost.checkTimeLimit();
        requireLost(lost);

        GameEngine won = startedEngine(GameSettings.beginner());
        try {
            for (Cell cell : won.getBoard().getAllCells()) {
                if (cell instanceof NormalCell && !cell.isRevealed()) {
                    won.toggleFlag(cell.getRow(), cell.getCol());
                    won.revealCell(cell.getRow(), cell.getCol());
                }
            }
            throw new AssertionError("Expected the completed board to win");
        } catch (GameOverException result) {
            require(result.isWon(), "Revealing all safe cells must win");
        }
        int score = won.getPlayer().getScore();
        won.checkTimeLimit();
        require(won.getState() == GameState.WON, "Polling must preserve a win");
        require(won.getPlayer().getScore() == score, "Polling must not award another win bonus");
        require(!won.getTimer().isRunning(), "A finished game's timer must remain stopped");
    }

    private static void expiredConsole(String command) throws Exception {
        GameEngine engine = startedEngine(timedSettings());
        expire(engine);
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setIn(new ByteArrayInputStream((command + "\nn\n").getBytes(StandardCharsets.UTF_8)));
            System.setOut(captured);
            ConsoleUI console = new ConsoleUI();
            // Test the console loop without interactive setup or waiting thirty seconds.
            Field engineField = ConsoleUI.class.getDeclaredField("engine");
            engineField.setAccessible(true);
            engineField.set(console, engine);
            Method loop = ConsoleUI.class.getDeclaredMethod("runGameLoop");
            loop.setAccessible(true);
            require(Boolean.FALSE.equals(loop.invoke(console)), "Declining replay must exit the loop");
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
        require(output.toString(StandardCharsets.UTF_8).contains("Game over!"),
                "The console must display the engine's timeout result");
        requireLost(engine);
    }

    private static void requireLost(GameEngine engine) {
        require(engine.getState() == GameState.LOST, "Timeout must end the game");
        require(!engine.getTimer().isRunning(), "Timeout must stop the timer");
    }

    private static void expectLoss(TestBody action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected a GameOverException for a loss");
        } catch (GameOverException result) {
            require(!result.isWon(), "Timeout must produce a loss");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record TestCase(String name, TestBody body) {}

    @FunctionalInterface
    private interface TestBody {
        void run() throws Exception;
    }
}
