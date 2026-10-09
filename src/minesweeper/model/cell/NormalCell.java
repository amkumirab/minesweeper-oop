package minesweeper.model.cell;

import minesweeper.model.Board;
import minesweeper.model.Board.CellRevealOutcome;
import minesweeper.model.Player;

/**
 * A safe cell that shows the count of dangerous neighbours when revealed.
 *
 * Inheritance: extends Cell, inheriting encapsulation and reveal guard.
 * Polymorphism (overriding): onReveal() and getRevealedSymbol() are specific to this type.
 */
public class NormalCell extends Cell {

    private int adjacentMines;   // count of neighbouring MineCell objects
    private int adjacentTraps;   // count of neighbouring TrapCell objects
    private int adjacentOtherDanger; // other neighbouring Explodable cells

    // ── constructor ──────────────────────────────────────────────────────────
    public NormalCell(int row, int col) {
        super(row, col);
        this.adjacentMines = 0;
        this.adjacentTraps = 0;
        this.adjacentOtherDanger = 0;
    }

    // ── Cell hooks ───────────────────────────────────────────────────────────

    @Override
    protected CellRevealOutcome onReveal(Player player, Board<? extends Cell> board) {
        player.addScore(1);
        return CellRevealOutcome.NORMAL;
    }

    // ── adjacency info ───────────────────────────────────────────────────────

    public int getAdjacentMines()  { return adjacentMines; }
    public int getAdjacentTraps()  { return adjacentTraps; }

    public void setAdjacentMines(int count) { this.adjacentMines = count; }
    public void setAdjacentTraps(int count) { this.adjacentTraps = count; }

    /** Keeps custom dangerous cells separate from the mine and trap counts. */
    public void setAdjacentOtherDanger(int count) { this.adjacentOtherDanger = count; }

    /**
     * Total neighbouring danger: mines, traps, and any other Explodable cells.
     */
    public int getAdjacentDanger() { return adjacentMines + adjacentTraps + adjacentOtherDanger; }

    // ── display ──────────────────────────────────────────────────────────────

    @Override
    public String getRevealedSymbol() {
        int danger = getAdjacentDanger();
        return danger == 0 ? " " : String.valueOf(danger);
    }

    @Override
    public String getType() { return "NORMAL"; }
}
