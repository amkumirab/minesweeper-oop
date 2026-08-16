package minesweeper.model.cell;

/**
 * A safe cell that shows the count of dangerous neighbours when revealed.
 *
 * Inheritance: extends Cell, inheriting encapsulation and reveal guard.
 * Polymorphism (overriding): onReveal() and getRevealedSymbol() are specific to this type.
 */
public class NormalCell extends Cell {

    private int adjacentMines;   // count of neighbouring MineCell objects
    private int adjacentTraps;   // count of neighbouring TrapCell objects

    // ── constructor ──────────────────────────────────────────────────────────
    public NormalCell(int row, int col) {
        super(row, col);
        this.adjacentMines = 0;
        this.adjacentTraps = 0;
    }

    // ── Cell hooks ───────────────────────────────────────────────────────────

    @Override
    protected void onReveal() {
        // Nothing extra needed for a normal cell — the base class already
        // marks it revealed; the board handles flood-fill from outside.
    }

    // ── adjacency info ───────────────────────────────────────────────────────

    public int getAdjacentMines()  { return adjacentMines; }
    public int getAdjacentTraps()  { return adjacentTraps; }

    public void setAdjacentMines(int count) { this.adjacentMines = count; }
    public void setAdjacentTraps(int count) { this.adjacentTraps = count; }

    /**
     * Combined danger score: mines + traps shown to the player.
     */
    public int getAdjacentDanger() { return adjacentMines + adjacentTraps; }

    // ── display ──────────────────────────────────────────────────────────────

    @Override
    public String getRevealedSymbol() {
        int danger = getAdjacentDanger();
        return danger == 0 ? " " : String.valueOf(danger);
    }

    @Override
    public String getType() { return "NORMAL"; }
}
