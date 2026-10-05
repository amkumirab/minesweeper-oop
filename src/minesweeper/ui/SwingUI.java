package minesweeper.ui;

import minesweeper.engine.*;
import minesweeper.exceptions.GameOverException;
import minesweeper.exceptions.InvalidCoordinateException;
import minesweeper.model.Board;
import minesweeper.model.cell.Cell;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Swing-based graphical interface for the Minesweeper game.
 *
 * Composition: SwingUI owns a GameEngine and a grid of CellButton objects.
 * It delegates all game logic to the engine and only handles rendering.
 */
public class SwingUI extends JFrame {

    private static final long serialVersionUID = 1L;

    // ── game state ─────────────────────────────────────────────────────────
    private GameEngine   engine;
    private GameSettings settings;
    private CellButton[][] buttons;

    // ── header widgets ─────────────────────────────────────────────────────
    private JLabel mineCountLabel;
    private JLabel timerLabel;
    private JLabel livesLabel;
    private JLabel scoreLabel;
    private JButton faceButton;
    private JButton undoButton;
    private JLabel messageLabel;

    // ── panels ─────────────────────────────────────────────────────────────
    private JPanel boardPanel;
    private Timer  swingTimer;

    // ── constructor ────────────────────────────────────────────────────────

    public SwingUI() {
        super("Minesweeper");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        showSetupAndStart();
    }

    // ── setup / new game ───────────────────────────────────────────────────

    private void showSetupAndStart() {
        SetupDialog dialog = new SetupDialog(this);
        dialog.setVisible(true);
        settings = dialog.getResult();

        if (settings == null) {
            System.exit(0); // user closed the dialog
        }

        startNewGame();
    }

    private void startNewGame() {
        // Stop old timer if running
        if (swingTimer != null) swingTimer.stop();

        // Create engine
        engine = new GameEngine(settings, "Player");
        engine.initialise();

        // Remove old content
        getContentPane().removeAll();
        setLayout(new BorderLayout(0, 0));

        // ── header ──────────────────────────────────────────────────────────
        JPanel header = buildHeader();
        add(header, BorderLayout.NORTH);

        // ── message bar ─────────────────────────────────────────────────────
        messageLabel = new JLabel(" ");
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(messageLabel, BorderLayout.SOUTH);

        // ── board ───────────────────────────────────────────────────────────
        int rows = settings.getRows();
        int cols = settings.getCols();
        boardPanel = new JPanel(new GridLayout(rows, cols, 0, 0));
        boardPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        buttons = new CellButton[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                CellButton btn = new CellButton(r, c);
                final int fr = r, fc = c;

                btn.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (engine.getState() != GameState.IN_PROGRESS
                                && engine.getState() != GameState.NOT_STARTED) return;

                        if (SwingUtilities.isRightMouseButton(e)) {
                            handleFlag(fr, fc);
                        } else if (SwingUtilities.isLeftMouseButton(e)) {
                            handleReveal(fr, fc);
                        }
                    }
                });

                buttons[r][c] = btn;
                boardPanel.add(btn);
            }
        }

        add(boardPanel, BorderLayout.CENTER);

        // ── timer ───────────────────────────────────────────────────────────
        swingTimer = new Timer(1000, e -> updateTimerDisplay());
        swingTimer.start();

        // ── final ───────────────────────────────────────────────────────────
        refreshAllCells();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ── header ─────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        header.setBackground(new Color(200, 200, 200));

        // Mine count (left)
        mineCountLabel = new JLabel(formatMineCount());
        mineCountLabel.setFont(new Font("Monospaced", Font.BOLD, 22));
        mineCountLabel.setForeground(Color.RED);
        mineCountLabel.setOpaque(true);
        mineCountLabel.setBackground(Color.BLACK);
        mineCountLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        // Face button (center)
        faceButton = new JButton("\uD83D\uDE42"); // 🙂
        faceButton.setFont(new Font("SansSerif", Font.PLAIN, 24));
        faceButton.setFocusPainted(false);
        faceButton.setPreferredSize(new Dimension(44, 44));
        faceButton.addActionListener(e -> showSetupAndStart());

        // Timer (right)
        timerLabel = new JLabel("00:00");
        timerLabel.setFont(new Font("Monospaced", Font.BOLD, 22));
        timerLabel.setForeground(Color.RED);
        timerLabel.setOpaque(true);
        timerLabel.setBackground(Color.BLACK);
        timerLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        // Lives + Score (below)
        livesLabel = new JLabel(formatLives());
        livesLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        undoButton = new JButton("↩ Undo");
        undoButton.setEnabled(false);
        undoButton.setFocusPainted(false);
        undoButton.addActionListener(e -> handleUndo());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(mineCountLabel);
        if (settings.isLivesEnabled()) left.add(livesLabel);
        if (settings.isBonusesEnabled()) left.add(undoButton);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        right.add(scoreLabel);
        right.add(timerLabel);

        header.add(left, BorderLayout.WEST);
        header.add(faceButton, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    // ── actions ─────────────────────────────────────────────────────────────

    private void handleReveal(int row, int col) {
        try {
            RevealResult result = engine.revealCell(row, col);
            setMessage(result.getMessage());

            if (result.getOutcome() == RevealResult.Outcome.MINE
                    || result.getOutcome() == RevealResult.Outcome.TRAP) {
                faceButton.setText("\uD83D\uDE2E"); // 😮
                // Reset face after 500ms if still playing
                Timer t = new Timer(500, e -> {
                    if (engine.getState() == GameState.IN_PROGRESS) {
                        faceButton.setText("\uD83D\uDE42"); // 🙂
                    }
                });
                t.setRepeats(false);
                t.start();
            }

        } catch (GameOverException e) {
            handleGameOver(e.isWon());
        } catch (InvalidCoordinateException e) {
            setMessage("Invalid coordinate.");
        }

        refreshAllCells();
        updateHeader();
    }

    private void handleFlag(int row, int col) {
        try {
            engine.toggleFlag(row, col);
        } catch (GameOverException e) {
            handleGameOver(e.isWon());
        } catch (InvalidCoordinateException e) {
            // ignore
        }
        refreshAllCells();
        updateHeader();
    }

    private void handleUndo() {
        try {
            if (engine.useUndo()) {
                setMessage("↩ Undo used! A safe cell was revealed for you.");
            } else {
                setMessage("No undo token available.");
            }
        } catch (GameOverException e) {
            handleGameOver(e.isWon());
        }
        refreshAllCells();
        updateHeader();
    }

    private void handleGameOver(boolean won) {
        if (swingTimer != null) swingTimer.stop();

        if (won) {
            faceButton.setText("\uD83D\uDE0E"); // 😎
            setMessage("🎉 You won! Score: " + engine.getPlayer().getScore());
        } else {
            faceButton.setText("\uD83D\uDE35"); // 😵
            setMessage("💥 Game Over! Click the face to play again.");
        }

        refreshAllCells();
        updateHeader();
    }

    // ── refresh ─────────────────────────────────────────────────────────────

    private void refreshAllCells() {
        Board<Cell> board = engine.getBoard();
        int rows = settings.getRows();
        int cols = settings.getCols();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = null;
                try {
                    cell = board.getCell(r, c);
                } catch (InvalidCoordinateException ignored) {}
                buttons[r][c].updateFromCell(cell);
            }
        }
    }

    private void updateHeader() {
        mineCountLabel.setText(formatMineCount());
        livesLabel.setText(formatLives());
        scoreLabel.setText("Score: " + engine.getPlayer().getScore());
        undoButton.setEnabled(engine.getState() == GameState.IN_PROGRESS
                && engine.getPlayer().isUndoAvailable());
    }

    private void updateTimerDisplay() {
        try {
            engine.checkTimeLimit();
        } catch (GameOverException e) {
            handleGameOver(e.isWon());
        }

        GameTimer timer = engine.getTimer();
        if (timer != null) {
            timerLabel.setText(settings.isTimeLimitEnabled()
                    ? timer.getFormattedRemaining()
                    : timer.getFormattedElapsed());
        }
    }

    // ── formatting helpers ──────────────────────────────────────────────────

    private String formatMineCount() {
        int count = engine.getRemainingMineCount();
        return String.format(" %03d ", Math.max(count, 0));
    }

    private String formatLives() {
        if (!settings.isLivesEnabled()) return "";
        int lives = engine.getPlayer().getLives();
        return "  " + "❤".repeat(Math.max(0, lives)) + " ";
    }

    private void setMessage(String msg) {
        if (messageLabel != null) {
            messageLabel.setText(msg);
        }
    }
}
