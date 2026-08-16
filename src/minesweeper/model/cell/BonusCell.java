package minesweeper.model.cell;

import minesweeper.interfaces.Rewardable;
import minesweeper.model.Board;
import minesweeper.model.BonusType;
import minesweeper.model.Player;

/**
 * A cell that hides a reward.  Revealing it benefits the player.
 *
 * Inheritance  : extends Cell.
 * Subtyping    : implements Rewardable (multityping).
 * Polymorphism : onReveal() marks collection; applyReward() grants the bonus.
 * Composition  : holds a BonusType value that determines what reward to grant.
 */
public class BonusCell extends Cell implements Rewardable {

    private final BonusType bonusType;
    private       boolean   collected;

    // ── constructor ──────────────────────────────────────────────────────────

    public BonusCell(int row, int col, BonusType bonusType) {
        super(row, col);
        this.bonusType = bonusType;
        this.collected = false;
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public BonusType getBonusType()  { return bonusType; }
    public boolean   isCollected()   { return collected;  }

    // ── Cell hook ────────────────────────────────────────────────────────────

    @Override
    protected void onReveal() {
        // Reward is applied by the GameEngine after reveal() to keep
        // the Cell free of board / engine references at this stage.
    }

    // ── Rewardable ───────────────────────────────────────────────────────────

    /**
     * Applies the bonus once only.  Idempotent — calling twice is safe.
     */
    @Override
    public void applyReward(Player player, Board<? extends Cell> board) {
        if (collected) return;
        collected = true;

        switch (bonusType) {
            case EXTRA_LIFE       -> player.gainLife();
            case REVEAL_SAFE_CELLS -> board.revealRandomSafeCells(3, player);
            case UNDO_LAST_MOVE    -> player.setUndoAvailable(true);
        }

        player.addScore(bonusType.getScoreValue());
    }

    @Override
    public String getRewardDescription() {
        return bonusType.getDisplayName() + " — " + bonusType.getDescription();
    }

    // ── display ──────────────────────────────────────────────────────────────

    @Override
    public String getRevealedSymbol() { return "B"; }

    @Override
    public String getType() { return "BONUS"; }
}
