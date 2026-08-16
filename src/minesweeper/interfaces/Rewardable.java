package minesweeper.interfaces;

import minesweeper.model.Board;
import minesweeper.model.Player;
import minesweeper.model.cell.Cell;

/**
 * Abstraction / Subtyping: Marks a cell as rewarding — revealing it benefits the player.
 * Extensibility: new reward types can be created by implementing this interface.
 */
public interface Rewardable {

    /**
     * Applies the reward to the player and/or the board.
     *
     * @param player the receiving player
     * @param board  the current board (some rewards interact with it)
     */
    void applyReward(Player player, Board<? extends Cell> board);

    /**
     * Returns a human-readable description of the reward.
     */
    String getRewardDescription();
}
