package minesweeper.engine;

/**
 * Value object returned by {@link GameEngine#revealCell(int, int)}.
 * Carries the outcome type and a human-readable message for the UI layer.
 *
 * Separating result data from the engine keeps the UI independent of
 * game-logic internals (single-responsibility).
 */
public final class RevealResult {

    public enum Outcome {
        NORMAL,           // safe cell revealed
        EMPTY_FLOOD,      // empty cell that triggered a flood reveal
        MINE,             // mine hit — life lost (game may continue with lives)
        TRAP,             // trap triggered — effect applied
        BONUS,            // bonus collected — reward applied
        ALREADY_REVEALED, // no-op: cell was already open
        FLAGGED,          // no-op: flagged cells are protected
        FROZEN            // player's FREEZE debuff consumed — random safe cell revealed
    }

    // ── fields ───────────────────────────────────────────────────────────────

    private final Outcome outcome;
    private final int     row;
    private final int     col;
    private final String  message;

    // ── constructor ──────────────────────────────────────────────────────────

    public RevealResult(Outcome outcome, int row, int col, String message) {
        this.outcome = outcome;
        this.row     = row;
        this.col     = col;
        this.message = message;
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public Outcome getOutcome() { return outcome; }
    public int     getRow()     { return row;     }
    public int     getCol()     { return col;     }
    public String  getMessage() { return message; }
}
