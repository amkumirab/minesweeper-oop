package minesweeper.interfaces;

/**
 * Abstraction: Represents any board element the player can flag as a suspected mine.
 * Multityping: a Cell can be both Revealable and Flaggable simultaneously.
 */
public interface Flaggable {

    /** Places a flag on this element. */
    void flag();

    /** Removes the flag from this element. */
    void unflag();

    /** Returns whether this element is currently flagged. */
    boolean isFlagged();
}
