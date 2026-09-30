package minesweeper.model.cell;

import minesweeper.interfaces.Explodable;
import minesweeper.model.Board;
import minesweeper.model.Board.CellRevealOutcome;
import minesweeper.model.Player;

/**
 * A cell that conceals a mine.
 *
 * Inheritance    : extends Cell.
 * Subtyping      : also implements Explodable (multityping).
 * Polymorphism   : explode() applies mine-specific damage; getRevealedSymbol() shows the mine.
 * Encapsulation  : triggered flag is private; only accessible via hasTriggered().
 */
public class MineCell extends Cell implements Explodable {

    private boolean triggered;

    // ── constructor ──────────────────────────────────────────────────────────
    public MineCell(int row, int col) {
        super(row, col);
        this.triggered = false;
    }

    // ── Cell hook ────────────────────────────────────────────────────────────

    @Override
    protected CellRevealOutcome onReveal(Player player, Board<? extends Cell> board) {
        explode(player);
        return CellRevealOutcome.MINE;
    }

    // ── Explodable ───────────────────────────────────────────────────────────

    /**
     * Detonates the mine: the player loses one life.
     * Polymorphism (overriding): different from TrapCell's explode().
     */
    @Override
    public void explode(Player player) {
        this.triggered = true;
        player.loseLife();
    }

    @Override
    public boolean hasTriggered() { return triggered; }

    // ── display ──────────────────────────────────────────────────────────────

    @Override
    public String getRevealedSymbol() { return "*"; }

    @Override
    public String getType() { return "MINE"; }
}
