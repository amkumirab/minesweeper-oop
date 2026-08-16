package minesweeper.model;

/**
 * Enumerates the possible rewards a BonusCell can grant.
 * Bonus definitions are centralized here; new constants must also be handled
 * by the reward dispatch in BonusCell.
 */
public enum BonusType {

    EXTRA_LIFE(
            "Extra Life",
            "Grants you one additional life! ❤",
            10),

    REVEAL_SAFE_CELLS(
            "Safe Reveal",
            "Automatically reveals 3 random safe cells for you! 🔍",
            15),

    UNDO_LAST_MOVE(
            "Undo Token",
            "Gives you the ability to undo your next mistake! ↩",
            20);

    // ── fields ──────────────────────────────────────────────────────────────
    private final String displayName;
    private final String description;
    private final int scoreValue;

    // ── constructor ──────────────────────────────────────────────────────────
    BonusType(String displayName, String description, int scoreValue) {
        this.displayName  = displayName;
        this.description  = description;
        this.scoreValue   = scoreValue;
    }

    // ── getters ──────────────────────────────────────────────────────────────
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int    getScoreValue()  { return scoreValue;  }
}
