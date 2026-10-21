package minesweeper.engine;

import minesweeper.model.cell.Cell;
import minesweeper.model.cell.NormalCell;

/** Immutable display data; never holds a reference to a mutable cell. */
public final class CellView {
    private final int row;
    private final int col;
    private final boolean revealed;
    private final boolean flagged;
    private final String type;
    private final String revealedSymbol;
    private final int adjacentDanger;

    CellView(Cell cell) {
        row = cell.getRow();
        col = cell.getCol();
        revealed = cell.isRevealed();
        flagged = cell.isFlagged();
        type = cell.getType();
        revealedSymbol = cell.getRevealedSymbol();
        adjacentDanger = cell instanceof NormalCell normal ? normal.getAdjacentDanger() : 0;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public boolean isRevealed() { return revealed; }
    public boolean isFlagged() { return flagged; }
    public String getType() { return type; }
    public String getRevealedSymbol() { return revealedSymbol; }
    public int getAdjacentDanger() { return adjacentDanger; }
}
