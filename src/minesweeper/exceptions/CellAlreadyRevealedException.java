package minesweeper.exceptions;

/**
 * Thrown when the player attempts to reveal a cell that is already revealed.
 * Unchecked — represents a programming/logic error rather than recoverable input.
 */
public class CellAlreadyRevealedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int row;
    private final int col;

    public CellAlreadyRevealedException(int row, int col) {
        super(String.format("Cell at (%d, %d) is already revealed.", row + 1, col + 1));
        this.row = row;
        this.col = col;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
}
