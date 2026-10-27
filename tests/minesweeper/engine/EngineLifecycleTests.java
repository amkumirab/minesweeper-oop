package minesweeper.engine;

import java.util.List;
import minesweeper.exceptions.GameOverException;
import minesweeper.model.cell.*;

/** Checks complete session reset without changing the engine's public boundary. */
public final class EngineLifecycleTests {
    private EngineLifecycleTests() {}

    public static void main(String[] args) throws Exception {
        List<Case> cases = List.of(new Case("Reset an active session", EngineLifecycleTests::active),
                new Case("Reset after loss", EngineLifecycleTests::lost),
                new Case("Reset after victory", EngineLifecycleTests::won));
        int failures = 0;
        for (Case test : cases) {
            try { test.body().run(); System.out.println("PASS: " + test.name()); }
            catch (Exception | AssertionError error) {
                failures++;
                System.err.println("FAIL: " + test.name() + " - " + error);
            }
        }
        System.out.println((cases.size() - failures) + "/" + cases.size() + " lifecycle tests passed.");
        require(failures == 0, "Engine lifecycle regressions: " + failures);
    }

    private static GameEngine started(GameSettings settings) throws Exception {
        GameEngine engine = new GameEngine(settings, "Player");
        engine.initialise();
        flagOthers(engine);
        engine.revealCell(0, 0);
        return engine;
    }

    private static void flagOthers(GameEngine engine) throws Exception {
        for (Cell cell : engine.getBoard().getAllCells()) {
            if (cell.getRow() != 0 || cell.getCol() != 0) engine.toggleFlag(cell.getRow(), cell.getCol());
        }
    }

    private static void active() throws Exception {
        GameEngine engine = started(new GameSettings.Builder().livesEnabled(true).maxLives(3).build());
        hitMine(engine);
        engine.getPlayer().addDebuff("FREEZE");
        engine.getPlayer().setUndoAvailable(true);
        require(engine.getLives() == 2 && engine.isUndoAvailable() && engine.isTimerRunning(), "Expected an altered active session");
        verifyReset(engine, 3);
    }

    private static void lost() throws Exception {
        GameEngine engine = started(GameSettings.beginner());
        try { hitMine(engine); throw new AssertionError("Expected loss"); }
        catch (GameOverException loss) { require(!loss.isWon(), "Expected a loss"); }
        require(engine.getLives() == 0, "Expected no remaining lives");
        verifyReset(engine, 1);
    }

    private static void hitMine(GameEngine engine) throws Exception {
        Cell mine = engine.getBoard().getAllCells().stream()
                .filter(cell -> cell instanceof MineCell).findFirst().orElseThrow();
        engine.toggleFlag(mine.getRow(), mine.getCol());
        engine.revealCell(mine.getRow(), mine.getCol());
    }

    private static void won() throws Exception {
        GameEngine engine = started(GameSettings.beginner());
        try {
            for (Cell cell : engine.getBoard().getAllCells()) {
                if (cell instanceof NormalCell && !cell.isRevealed()) {
                    engine.toggleFlag(cell.getRow(), cell.getCol());
                    engine.revealCell(cell.getRow(), cell.getCol());
                }
            }
            throw new AssertionError("Expected victory");
        } catch (GameOverException win) { require(win.isWon(), "Expected victory"); }
        require(engine.getScore() == 171, "Expected the old game's win bonus");
        verifyReset(engine, 1);
    }

    private static void verifyReset(GameEngine engine, int expectedLives) throws Exception {
        GameSettings settings = engine.getSettings();
        GameTimer oldTimer = engine.getTimer();
        engine.initialise();
        require(engine.getLives() == expectedLives && engine.getScore() == 0, "Reset must restore lives and clear score");
        require(!engine.isUndoAvailable() && engine.getPlayer().getDebuffs().isEmpty(), "Reset must clear tokens and debuffs");
        require(engine.getPlayer().peekLastMove() == null, "Reset must clear move history");
        require(engine.getSettings() == settings && engine.getPlayerName().equals("Player"), "Reset must preserve configuration and name");
        require(engine.getState() == GameState.NOT_STARTED && !engine.isTimerRunning(), "Reset must return to an unstarted session");
        require(!oldTimer.isRunning() && oldTimer != engine.getTimer(), "Reset must stop the old timer and replace it");
        require(engine.getFormattedElapsed().equals("00:00"), "Reset must clear elapsed time");
        require(engine.getBoard().getRevealedSafeCells() == 0 && engine.getBoard().getFlaggedCount() == 0,
                "Reset must clear board progress");
        require(engine.getBoard().getAllCells().stream().allMatch(cell -> !cell.isRevealed() && !cell.isFlagged()),
                "Reset must create a hidden, unflagged board");
        flagOthers(engine);
        engine.revealCell(0, 0);
        require(engine.getState() == GameState.IN_PROGRESS && engine.getScore() == 1 && engine.getLives() == expectedLives,
                "A new session must play normally after reset");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Case(String name, Action body) {}
    @FunctionalInterface private interface Action { void run() throws Exception; }
}
