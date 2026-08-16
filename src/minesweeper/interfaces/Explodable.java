package minesweeper.interfaces;

import minesweeper.model.Player;

/**
 * Abstraction / Subtyping: Marks a cell as dangerous — it can detonate and affect the Player.
 * Both MineCell and TrapCell implement this interface (multityping / subtyping polymorphism).
 */
public interface Explodable {

    /**
     * Triggers the explosion / trap effect on the given player.
     *
     * @param player the player who stepped on this cell
     */
    void explode(Player player);

    /**
     * Returns whether this cell has already been triggered.
     */
    boolean hasTriggered();
}
