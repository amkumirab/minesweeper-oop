package minesweeper.model.cell;

import minesweeper.interfaces.Explodable;
import minesweeper.model.Board;
import minesweeper.model.Board.CellRevealOutcome;
import minesweeper.model.Player;

/**
 * A cell that hides a trap.  Unlike a mine, a trap may apply a variety of
 * negative effects rather than always removing a life.
 *
 * Inheritance  : extends Cell.
 * Subtyping    : implements Explodable — shares the "dangerous cell" contract
 *                with MineCell without duplicate code.
 * Polymorphism : explode() has its own behaviour distinct from MineCell.
 * Extensibility: TrapEffect keeps the supported effects centralized and explicit.
 */
public class TrapCell extends Cell implements Explodable {

    // ── inner enum ───────────────────────────────────────────────────────────

    /**
     * New constants must also be handled by explode(Player).
     */
    public enum TrapEffect {
        LOSE_LIFE(
                "Lose a Life",
                "You lose one life! ❤ Gone."),
        REVEAL_RANDOM_MINE(
                "Mine Spotlight",
                "A random hidden mine is forced onto the board! 👁"),
        FREEZE_NEXT_MOVE(
                "Freeze",
                "Your next move is frozen — a random unrevealed safe cell gets revealed instead! 🧊");

        private final String displayName;
        private final String description;

        TrapEffect(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName()  { return displayName; }
        public String getDescription()  { return description; }
    }

    // ── fields ───────────────────────────────────────────────────────────────

    private final TrapEffect effect;
    private       boolean    triggered;

    // ── constructor ──────────────────────────────────────────────────────────

    public TrapCell(int row, int col, TrapEffect effect) {
        super(row, col);
        this.effect    = effect;
        this.triggered = false;
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public TrapEffect getEffect() { return effect; }

    // ── Cell hook ────────────────────────────────────────────────────────────

    @Override
    protected CellRevealOutcome onReveal(Player player, Board<? extends Cell> board) {
        explode(player);
        return CellRevealOutcome.TRAP;
    }

    @Override
    public String getRevealDescription() { return effect.getDescription(); }

    // ── Explodable ───────────────────────────────────────────────────────────

    @Override
    public void explode(Player player) {
        this.triggered = true;
        switch (effect) {
            case LOSE_LIFE         -> player.loseLife();
            case REVEAL_RANDOM_MINE -> player.addDebuff("REVEAL_MINE");
            case FREEZE_NEXT_MOVE   -> player.addDebuff("FREEZE");
        }
    }

    @Override
    public boolean hasTriggered() { return triggered; }

    // ── display ──────────────────────────────────────────────────────────────

    @Override
    public String getRevealedSymbol() { return "T"; }

    @Override
    public String getType() { return "TRAP"; }
}
