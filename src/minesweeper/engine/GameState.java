package minesweeper.engine;

/**
 * Represents the lifecycle stages of a single game session.
 */
public enum GameState {
    NOT_STARTED("Game has not started yet."),
    IN_PROGRESS("Game is in progress."),
    WON("You won! Congratulations! 🏆"),
    LOST("Game over! Better luck next time. 💥");

    private final String description;

    GameState(String description) { this.description = description; }

    public String getDescription() { return description; }
}
