package minesweeper;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import javax.swing.SwingUtilities;
import minesweeper.engine.CellView;
import minesweeper.engine.GameEngine;
import minesweeper.engine.GameSettings;
import minesweeper.engine.GameState;
import minesweeper.engine.GameTimer;
import minesweeper.exceptions.GameOverException;
import minesweeper.exceptions.InvalidCoordinateException;
import minesweeper.model.Board;
import minesweeper.model.Player;
import minesweeper.model.cell.Cell;
import minesweeper.ui.CellButton;

/** Checks the public boundary between the engine and its callers. */
public final class EncapsulationTests {
    private EncapsulationTests() {}

    public static void main(String[] args) throws Exception {
        List<TestCase> tests = List.of(
                new TestCase("Public engine methods hide mutable game objects", EncapsulationTests::publicBoundary),
                new TestCase("Cell views contain only immutable display data", EncapsulationTests::immutableView),
                new TestCase("Cell snapshots do not change after engine actions", EncapsulationTests::stableSnapshots),
                new TestCase("Public actions keep player and flag queries consistent", EncapsulationTests::actionQueries),
                new TestCase("Cell queries validate coordinates", EncapsulationTests::invalidCoordinates),
                new TestCase("Loss updates fresh cell views without changing old snapshots", EncapsulationTests::lossSnapshots),
                new TestCase("Time queries do not start the timer", EncapsulationTests::timerQueries),
                new TestCase("Unlimited time queries preserve infinity", EncapsulationTests::unlimitedQueries));
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
        System.out.println((tests.size() - failures) + "/" + tests.size() + " encapsulation tests passed.");
        require(failures == 0, "Encapsulation regression tests failed: " + failures);
    }

    private static void publicBoundary() {
        for (Method method : GameEngine.class.getMethods()) {
            Class<?> result = method.getReturnType();
            if (Board.class.isAssignableFrom(result) || Player.class.isAssignableFrom(result)
                    || Cell.class.isAssignableFrom(result) || GameTimer.class.isAssignableFrom(result)) {
                throw new AssertionError("Public engine method exposes mutable state: " + method.getName());
            }
        }
    }

    private static void immutableView() {
        require(Modifier.isFinal(CellView.class.getModifiers()), "CellView must not allow mutable subclasses");
        for (Field field : CellView.class.getDeclaredFields()) {
            require(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()),
                    "View fields must be private and final: " + field.getName());
            require(field.getType().isPrimitive() || field.getType() == String.class,
                    "Views must not retain mutable references: " + field.getName());
        }
        for (Method method : CellView.class.getDeclaredMethods()) {
            require(method.getParameterCount() == 0
                            && (method.getName().startsWith("get") || method.getName().startsWith("is")),
                    "CellView must expose queries only: " + method.getName());
        }
    }

    private static GameEngine startedEngine() throws Exception {
        GameEngine engine = new GameEngine(GameSettings.beginner(), "Player");
        engine.initialise();
        for (int row = 0; row < engine.getSettings().getRows(); row++) {
            for (int col = 0; col < engine.getSettings().getCols(); col++) {
                if (row != 0 || col != 0) engine.toggleFlag(row, col);
            }
        }
        engine.revealCell(0, 0);
        return engine;
    }

    private static void stableSnapshots() throws Exception {
        GameEngine engine = startedEngine();
        CellView flagged = engine.getCell(0, 1);
        engine.toggleFlag(0, 1);
        CellView hidden = engine.getCell(0, 1);
        engine.revealCell(0, 1);
        CellView revealed = engine.getCell(0, 1);
        require(flagged.isFlagged() && !flagged.isRevealed(), "Old flag snapshot changed");
        require(!hidden.isFlagged() && !hidden.isRevealed(), "Old hidden snapshot changed");
        require(revealed.isRevealed() && !revealed.isFlagged(), "Fresh view must reflect the reveal");
        require(revealed.getRow() == 0 && revealed.getCol() == 1, "View coordinates must match the cell");
        require(revealed.getType().equals("NORMAL"), "First-click neighbours must stay normal");
        require(revealed.getRevealedSymbol().equals(revealed.getAdjacentDanger() == 0
                ? " " : String.valueOf(revealed.getAdjacentDanger())), "View symbol must match danger count");
    }

    private static void actionQueries() throws Exception {
        GameEngine engine = startedEngine();
        require(engine.getPlayerName().equals("Player") && engine.getLives() == 1, "Player queries disagree");
        require(engine.getScore() == 1 && !engine.isUndoAvailable(), "Initial action queries disagree");
        int remaining = engine.getRemainingMineCount();
        engine.toggleFlag(0, 1);
        require(engine.getRemainingMineCount() == remaining + 1, "Flag count must follow engine actions");
        engine.revealCell(0, 1);
        require(engine.getScore() == 2, "A valid reveal must award its point");
        engine.revealCell(0, 1);
        require(engine.getScore() == 2, "Repeated queries and reveals must not add points");
    }

    private static void invalidCoordinates() throws Exception {
        GameEngine engine = startedEngine();
        try {
            engine.getCell(-1, 0);
            throw new AssertionError("Invalid cell query must throw");
        } catch (InvalidCoordinateException expected) {
            require(engine.getScore() == 1, "Invalid queries must not change the game");
        }
        require(engine.getState() == GameState.IN_PROGRESS, "Queries must not end the game");
    }

    private static void lossSnapshots() throws Exception {
        GameEngine engine = startedEngine();
        CellView target = null;
        for (int row = 0; row < engine.getSettings().getRows() && target == null; row++) {
            for (int col = 0; col < engine.getSettings().getCols() && target == null; col++) {
                CellView view = engine.getCell(row, col);
                if (view.getType().equals("MINE")) target = view;
            }
        }
        require(target != null, "Expected a mine");
        engine.toggleFlag(target.getRow(), target.getCol());
        try {
            engine.revealCell(target.getRow(), target.getCol());
            throw new AssertionError("Fatal mine must end the game");
        } catch (GameOverException result) {
            require(!result.isWon(), "A fatal mine cannot win");
        }
        require(target.isFlagged() && !target.isRevealed(), "Loss must not mutate a detached snapshot");
        CellView revealed = engine.getCell(target.getRow(), target.getCol());
        require(revealed.isRevealed() && !revealed.isFlagged(), "Fresh loss view must show the mine");
        require(engine.getLives() == 0 && !engine.isTimerRunning(), "Loss queries must reflect the engine");
        SwingUtilities.invokeAndWait(() -> {
            CellButton button = new CellButton(revealed.getRow(), revealed.getCol());
            button.updateFromCell(revealed);
            require(button.getText().equals("\uD83D\uDCA3") && !button.isEnabled(), "Swing must render the cell view");
        });
    }

    private static void timerQueries() throws Exception {
        GameEngine engine = new GameEngine(new GameSettings.Builder()
                .timeLimitEnabled(true).timeLimitSeconds(30).build(), "Player");
        engine.initialise();
        require(!engine.isTimerRunning(), "Querying must not start the timer");
        require(engine.getRemainingSeconds() == 30, "Unstarted countdown must remain intact");
        require(engine.getFormattedRemaining().equals("00:30"), "Countdown format changed");
        require(engine.getFormattedElapsed().equals("00:00"), "Unstarted elapsed time must be zero");
        require(engine.getState() == GameState.NOT_STARTED, "Time queries must not start the game");
    }

    private static void unlimitedQueries() throws Exception {
        GameEngine engine = new GameEngine(GameSettings.beginner(), "Player");
        engine.initialise();
        require(engine.getRemainingSeconds() == Long.MAX_VALUE, "Unlimited countdown must stay unbounded");
        require(engine.getFormattedRemaining().equals("∞"), "Unlimited countdown must display infinity");
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
