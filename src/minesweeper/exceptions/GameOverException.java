package minesweeper.exceptions;

/**
 * Signals that the game has ended — either through a win or a loss.
 * Using an exception for control flow here clearly separates the terminal game
 * event from normal reveal processing, keeping GameEngine clean.
 */
public class GameOverException extends Exception {

    private static final long serialVersionUID = 1L;

    private final boolean won;

    public GameOverException(boolean won) {
        super(won
                ? "Congratulations! You revealed all safe cells and won! 🏆"
                : "Game over! All lives lost or time ran out. 💥");
        this.won = won;
    }

    /** @return true if the game ended in a win, false for a loss */
    public boolean isWon() { return won; }
}
