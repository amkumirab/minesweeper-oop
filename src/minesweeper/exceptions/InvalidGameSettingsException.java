package minesweeper.exceptions;

/**
 * Thrown when the player supplies game settings that are logically impossible
 * (e.g., more mines than available cells, board too small, etc.).
 */
public class InvalidGameSettingsException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidGameSettingsException(String message) {
        super(message);
    }
}
