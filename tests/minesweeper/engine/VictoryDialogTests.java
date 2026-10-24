package minesweeper.engine;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import javax.swing.*;
import minesweeper.interfaces.Explodable;
import minesweeper.model.BonusType;
import minesweeper.model.cell.*;
import minesweeper.ui.*;

/** Optional integration tests that open real windows; run with gui-test.bat. */
public final class VictoryDialogTests {
    private VictoryDialogTests() {}

    public static void main(String[] args) throws Exception {
        require(!GraphicsEnvironment.isHeadless(), "GUI tests need a desktop display; use test.bat for headless tests");
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        java.util.Timer watchdog = new java.util.Timer(true);
        watchdog.schedule(new java.util.TimerTask() {
            @Override public void run() {
                System.err.println("FAIL: GUI tests timed out");
                System.exit(1);
            }
        }, 20_000);
        try {
            SwingUtilities.invokeAndWait(() -> {
                SwingUI ui = null;
                try {
                    ui = openGame();
                    SwingUI game = ui;
                    for (String kind : new String[]{"normal", "flood", "bonus", "undo"}) {
                        winningDialog(ui, kind);
                        System.out.println("PASS: " + kind + " win shows one dialog, preserves the board and resets for a new game");
                    }
                    GameEngine engine = prepare(ui, "normal");
                    Cell mine = engine.getBoard().getAllCells().stream()
                            .filter(cell -> cell instanceof MineCell).findFirst().orElseThrow();
                    engine.toggleFlag(mine.getRow(), mine.getCol());
                    expectDialogs(ui, () -> click(game, mine.getRow(), mine.getCol()), 0, false);
                    require(engine.getState() == GameState.LOST, "A fatal mine must lose, not congratulate");
                    System.out.println("PASS: Losing does not show a victory dialog");
                    System.out.println("5/5 GUI tests passed.");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                } finally {
                    if (ui != null) {
                        try { ((Timer) field(ui, "swingTimer")).stop(); }
                        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
                    }
                    for (Window window : Window.getWindows()) window.dispose();
                }
            });
        } finally {
            watchdog.cancel();
        }
    }

    private static SwingUI openGame() {
        Timer choosePreset = new Timer(25, event -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof SetupDialog setup && setup.isShowing()) {
                    JButton beginner = findButton(setup, "Beginner");
                    if (beginner != null) {
                        ((Timer) event.getSource()).stop();
                        beginner.doClick();
                    }
                }
            }
        });
        choosePreset.start();
        try { return new SwingUI(); }
        finally { choosePreset.stop(); }
    }

    private static GameEngine prepare(SwingUI ui, String kind) throws Exception {
        Field settings = SwingUI.class.getDeclaredField("settings");
        settings.setAccessible(true);
        settings.set(ui, new GameSettings.Builder().rows(9).cols(9).mineCount(kind.equals("flood") ? 1 : 10)
                .bonusesEnabled(kind.equals("undo")).bonusCount(1).build());
        invoke(ui, "startNewGame");
        GameEngine engine = (GameEngine) field(ui, "engine");
        Field random = GameEngine.class.getDeclaredField("random");
        random.setAccessible(true);
        ((java.util.Random) random.get(engine)).setSeed(0);
        for (Cell cell : engine.getBoard().getAllCells()) {
            if (cell.getRow() != 0 || cell.getCol() != 0) engine.toggleFlag(cell.getRow(), cell.getCol());
        }
        click(ui, 0, 0);
        Cell target = kind.equals("flood") ? engine.getBoard().getCell(1, 0) : engine.getBoard().getCell(0, 1);
        if (kind.equals("bonus")) {
            target = new BonusCell(0, 1, BonusType.EXTRA_LIFE);
            target.flag();
            engine.getBoard().setCell(0, 1, target);
        }
        if (kind.equals("undo")) {
            engine.getBoard().setCell(1, 0, new BonusCell(1, 0, BonusType.UNDO_LAST_MOVE));
            engine.revealCell(1, 0);
        }
        Cell floodNeighbour = kind.equals("flood") ? engine.getBoard().getCell(1, 1) : target;
        for (Cell cell : engine.getBoard().getAllCells()) {
            if (!(cell instanceof Explodable) && !cell.isRevealed() && cell != target && cell != floodNeighbour) {
                if (cell.isFlagged()) engine.toggleFlag(cell.getRow(), cell.getCol());
                engine.revealCell(cell.getRow(), cell.getCol());
            }
        }
        int remaining = kind.equals("flood") ? 2 : 1;
        require(engine.getBoard().getRevealedSafeCells() == engine.getBoard().getTotalSafeCells() - remaining,
                "Fixture must leave only the final safe targets hidden");
        if (kind.equals("flood")) {
            require(((NormalCell) target).getAdjacentDanger() == 0, "Flood target must actually have no adjacent danger");
            engine.toggleFlag(floodNeighbour.getRow(), floodNeighbour.getCol());
        }
        engine.toggleFlag(target.getRow(), target.getCol());
        invoke(ui, "refreshAllCells");
        invoke(ui, "updateHeader");
        return engine;
    }

    private static void winningDialog(SwingUI ui, String kind) throws Exception {
        GameEngine engine = prepare(ui, kind);
        Object board = field(ui, "boardPanel");
        expectDialogs(ui, () -> {
            if (kind.equals("undo")) ((JButton) field(ui, "undoButton")).doClick();
            else if (kind.equals("flood")) click(ui, 1, 0);
            else click(ui, 0, 1);
        }, 1, kind.equals("bonus"));
        require(engine.getState() == GameState.WON && !engine.isTimerRunning(), "Win must stop the engine timer");
        require(!((Timer) field(ui, "swingTimer")).isRunning(), "Win must stop the Swing timer");
        require(ui.isVisible() && field(ui, "engine") == engine && field(ui, "boardPanel") == board,
                "Dismissing the dialog must leave the completed game visible");
        require(((JLabel) field(ui, "messageLabel")).getText().contains("You won!"), "Footer must retain the result");
        expectDialogs(ui, () -> {
            invoke(ui, "handleReveal", new Class<?>[]{int.class, int.class}, 0, 1);
            invoke(ui, "updateTimerDisplay");
        }, 0, false);
    }

    private static void expectDialogs(SwingUI ui, Action action, int expected, boolean closeWithX) throws Exception {
        int[] shown = {0};
        Throwable[] failure = {null};
        Timer closeDialog = new Timer(25, event -> {
            for (Window window : ui.getOwnedWindows()) {
                if (window instanceof JDialog dialog && dialog.isShowing() && dialog.getTitle().equals("Victory")) {
                    shown[0]++;
                    try {
                        JOptionPane pane = findPane(dialog);
                        GameEngine engine = (GameEngine) field(ui, "engine");
                        require(dialog.isModal() && pane != null && pane.getMessageType() == JOptionPane.INFORMATION_MESSAGE,
                                "Victory must be an owned informational dialog");
                        String message = String.valueOf(pane.getMessage());
                        require(message.contains("You won!") && message.contains("Final score: " + engine.getScore()),
                                "Dialog must show the actual final score");
                        CellButton[][] buttons = (CellButton[][]) field(ui, "buttons");
                        require(!buttons[0][1].isEnabled() && !buttons[1][0].isEnabled() && !buttons[1][1].isEnabled(),
                                "Board must be refreshed before the modal dialog opens");
                        // A nested event must not open another dialog while the first is visible.
                        invoke(ui, "handleGameOver", new Class<?>[]{boolean.class}, true);
                        if (closeWithX) dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
                        else {
                            JButton ok = findButton(dialog, "OK");
                            require(ok != null, "Dialog must have an OK button");
                            ok.doClick();
                        }
                    } catch (Throwable error) {
                        failure[0] = error;
                    } finally { dialog.dispose(); }
                }
            }
        });
        closeDialog.start();
        try { action.run(); }
        finally { closeDialog.stop(); }
        if (failure[0] != null) throw new AssertionError("Invalid victory dialog", failure[0]);
        require(shown[0] == expected, "Expected " + expected + " victory dialogs, saw " + shown[0]);
    }

    private static void click(SwingUI ui, int row, int col) throws Exception {
        CellButton button = ((CellButton[][]) field(ui, "buttons"))[row][col];
        button.dispatchEvent(new MouseEvent(button, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, 10, 10, 1, false, MouseEvent.BUTTON1));
    }

    private static JButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && button.getText().contains(text)) return button;
            if (child instanceof Container container) {
                JButton found = findButton(container, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JOptionPane findPane(Container root) {
        if (root instanceof JOptionPane pane) return pane;
        for (Component child : root.getComponents()) {
            if (child instanceof Container container) {
                JOptionPane pane = findPane(container);
                if (pane != null) return pane;
            }
        }
        return null;
    }

    private static Object field(SwingUI ui, String name) throws ReflectiveOperationException {
        Field field = SwingUI.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(ui);
    }

    private static void invoke(SwingUI ui, String name) throws Exception {
        invoke(ui, name, new Class<?>[0]);
    }

    private static void invoke(SwingUI ui, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SwingUI.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(ui, args);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
}
