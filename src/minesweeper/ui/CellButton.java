package minesweeper.ui;

import minesweeper.model.cell.*;

import javax.swing.*;
import java.awt.*;

/**
 * A custom JButton representing a single cell on the Minesweeper board.
 * Handles its own visual appearance based on cell state.
 */
public class CellButton extends JButton {

    private static final long serialVersionUID = 1L;

    private static final int CELL_SIZE = 32;

    // Colors for number display (1-8)
    private static final Color[] NUMBER_COLORS = {
            null,                      // 0 — unused
            new Color(0, 0, 255),      // 1 — blue
            new Color(0, 128, 0),      // 2 — green
            new Color(255, 0, 0),      // 3 — red
            new Color(0, 0, 128),      // 4 — dark blue
            new Color(128, 0, 0),      // 5 — maroon
            new Color(0, 128, 128),    // 6 — teal
            new Color(0, 0, 0),        // 7 — black
            new Color(128, 128, 128)   // 8 — grey
    };

    private static final Color HIDDEN_BG       = new Color(189, 189, 189);
    private static final Color HIDDEN_BORDER    = new Color(255, 255, 255);
    private static final Color HIDDEN_SHADOW    = new Color(123, 123, 123);
    private static final Color REVEALED_BG      = new Color(215, 215, 215);
    private static final Color MINE_BG          = new Color(255, 50, 50);
    private static final Color TRAP_BG          = new Color(255, 165, 0);
    private static final Color BONUS_BG         = new Color(100, 220, 100);

    private final int row;
    private final int col;

    public CellButton(int row, int col) {
        this.row = row;
        this.col = col;
        setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
        setMinimumSize(new Dimension(CELL_SIZE, CELL_SIZE));
        setMaximumSize(new Dimension(CELL_SIZE, CELL_SIZE));
        setMargin(new Insets(0, 0, 0, 0));
        setFocusPainted(false);
        setFont(new Font("SansSerif", Font.BOLD, 14));
        setContentAreaFilled(false);
        setOpaque(true);
    }

    public int getRow() { return row; }
    public int getCol() { return col; }

    /**
     * Updates this button's appearance based on the cell state.
     */
    public void updateFromCell(Cell cell) {
        if (cell == null) {
            drawHidden();
            return;
        }

        if (cell.isFlagged()) {
            drawFlagged();
        } else if (!cell.isRevealed()) {
            drawHidden();
        } else {
            drawRevealed(cell);
        }
    }

    private void drawHidden() {
        setText("");
        setBackground(HIDDEN_BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 2, 0, 0, HIDDEN_BORDER),
                BorderFactory.createMatteBorder(0, 0, 2, 2, HIDDEN_SHADOW)
        ));
        setEnabled(true);
    }

    private void drawFlagged() {
        setText("\uD83D\uDEA9"); // 🚩
        setFont(new Font("SansSerif", Font.PLAIN, 16));
        setForeground(Color.RED);
        setBackground(HIDDEN_BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 2, 0, 0, HIDDEN_BORDER),
                BorderFactory.createMatteBorder(0, 0, 2, 2, HIDDEN_SHADOW)
        ));
        setHorizontalAlignment(CENTER);
        setEnabled(true);
    }

    private void drawRevealed(Cell cell) {
        setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
        setFont(new Font("SansSerif", Font.BOLD, 14));
        setEnabled(false);

        if (cell instanceof MineCell) {
            setText("\uD83D\uDCA3"); // 💣
            setFont(new Font("SansSerif", Font.PLAIN, 16));
            setBackground(MINE_BG);
            setDisabledIcon(null);

        } else if (cell instanceof TrapCell) {
            setText("\uD83E\uDEE4"); // 🪤  (using a visible fallback)
            setFont(new Font("SansSerif", Font.PLAIN, 16));
            setBackground(TRAP_BG);

        } else if (cell instanceof BonusCell) {
            setText("\uD83C\uDF81"); // 🎁
            setFont(new Font("SansSerif", Font.PLAIN, 16));
            setBackground(BONUS_BG);

        } else if (cell instanceof NormalCell normal) {
            int danger = normal.getAdjacentDanger();
            setBackground(REVEALED_BG);
            if (danger == 0) {
                setText("");
            } else {
                setText(String.valueOf(danger));
                setForeground(danger < NUMBER_COLORS.length ? NUMBER_COLORS[danger] : Color.BLACK);
            }
        } else {
            setBackground(REVEALED_BG);
            setText("");
        }
    }
}
