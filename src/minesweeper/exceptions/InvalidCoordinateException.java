package minesweeper.exceptions;

/**
 * Thrown when coordinates outside the board boundaries are used.
 * Custom checked exception — forces callers to handle bad input explicitly.
 */
public class InvalidCoordinateException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int row;
    private final int col;

    public InvalidCoordinateException(int row, int col, int maxRow, int maxCol) {
        super(String.format(
                "Invalid coordinates (%d, %d). Board dimensions are %d rows × %d cols.",
                row + 1, col + 1, maxRow, maxCol));
        this.row = row;
        this.col = col;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
}
