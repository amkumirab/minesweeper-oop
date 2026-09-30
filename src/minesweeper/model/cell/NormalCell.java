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

    // ── constructor ──────────────────────────────────────────────────────────
    public NormalCell(int row, int col) {
        super(row, col);
        this.adjacentMines = 0;
        this.adjacentTraps = 0;
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
