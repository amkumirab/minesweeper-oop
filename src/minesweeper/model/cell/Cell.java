package minesweeper.model.cell;

import minesweeper.exceptions.CellAlreadyRevealedException;
import minesweeper.interfaces.Flaggable;
import minesweeper.interfaces.Revealable;

/**
 * Abstract base class for every cell on the board.
 *
 * OOP concepts demonstrated here:
 *  - Encapsulation  : all state is private; subclasses access it through protected helpers
 *  - Abstraction    : reveal() and getRevealedSymbol() define the common cell contract
 *  - Inheritance    : NormalCell / MineCell / TrapCell / BonusCell all extend this class
 *  - Multityping    : implements both Revealable and Flaggable
 *  - Coercion       : toString() converts a Cell to String for display purposes
 */
public abstract class Cell implements Revealable, Flaggable {

    // ── encapsulated state ───────────────────────────────────────────────────
    private final int     row;
    private final int     col;
    private       boolean revealed;
    private       boolean flagged;

    // ── constructor ──────────────────────────────────────────────────────────
    protected Cell(int row, int col) {
        this.row      = row;
        this.col      = col;
        this.revealed = false;
        this.flagged  = false;
    }

    // ── Revealable ───────────────────────────────────────────────────────────

    /**
     * Template-method pattern: validates the pre-condition, then delegates
     * to {@link #onReveal()} for type-specific behaviour.
     */
    @Override
    public final void reveal() {
        if (revealed) {
            throw new CellAlreadyRevealedException(row, col);
        }
        revealed = true;
        onReveal();   // hook for subclasses
    }

    /**
     * Called by {@link #reveal()} after the cell is marked as revealed.
     * Subclasses override this to perform their specific reveal logic
     * (e.g., MineCell arms an explosion, BonusCell notes the collection).
     *
     * Demonstrates <b>overriding polymorphism</b>.
     */
    protected abstract void onReveal();

    @Override
    public boolean isRevealed() { return revealed; }

    /**
     * Force-reveals this cell without the already-revealed guard.
     * Used internally (e.g., game-over "show all mines") — not for player actions.
     */
    public void forceReveal() { this.revealed = true; }

    // ── Flaggable ────────────────────────────────────────────────────────────

    @Override
    public void flag() {
        if (!revealed) flagged = true;
    }

    @Override
    public void unflag() { flagged = false; }

    @Override
    public boolean isFlagged() { return flagged; }

    // ── position ─────────────────────────────────────────────────────────────

    public int getRow() { return row; }
    public int getCol() { return col; }

    // ── display ──────────────────────────────────────────────────────────────

    /**
     * Returns the single-character symbol shown when the cell is revealed.
     * Demonstrates <b>overriding polymorphism</b>.
     */
    public abstract String getRevealedSymbol();

    /**
     * Returns the logical type name of this cell (e.g., "MINE", "BONUS").
     */
    public abstract String getType();

    /**
     * Coercion: converts the cell to its display String automatically.
     * Hidden cells show "." (or "F" when flagged); revealed cells show their symbol.
     */
    @Override
    public String toString() {
        if (flagged)   return "F";
        if (!revealed) return ".";
        return getRevealedSymbol();
    }
}
