package minesweeper.model.cell;

import minesweeper.exceptions.CellAlreadyRevealedException;
import minesweeper.interfaces.Flaggable;
import minesweeper.interfaces.Revealable;
import minesweeper.model.Board;
import minesweeper.model.Board.CellRevealOutcome;
import minesweeper.model.Player;

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
     * Marks the cell as revealed without applying gameplay effects.
     * Use {@link #reveal(Player, Board)} for player actions.
     */
    @Override
    public final void reveal() {
        if (revealed) {
            throw new CellAlreadyRevealedException(row, col);
        }
        revealed = true;
    }

    /**
     * Template method: protects revealed and flagged cells, then applies
     * the subclass's effect exactly once. The board tracks the returned outcome.
     */
    public final CellRevealOutcome reveal(Player player, Board<? extends Cell> board) {
        if (revealed) return CellRevealOutcome.ALREADY_REVEALED;
        if (flagged) return CellRevealOutcome.FLAGGED;
        reveal();
        return onReveal(player, board);
    }

    /** Applies this cell's effect and returns NORMAL, MINE, TRAP, or BONUS. */
    protected abstract CellRevealOutcome onReveal(Player player, Board<? extends Cell> board);

    /** Description used by the engine without knowing the concrete cell class. */
    public String getRevealDescription() { return "Cell revealed."; }

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
