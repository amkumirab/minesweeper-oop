package minesweeper.interfaces;

/**
 * Abstraction: Defines the contract for any element that can be revealed on the board.
 * Separates the "what" (reveal behaviour) from the "how" (cell-type-specific implementation).
 */
public interface Revealable {

    /**
     * Reveals this element. Implementation varies by cell type (polymorphism).
     */
    void reveal();

    /**
     * Returns whether this element has already been revealed.
     */
    boolean isRevealed();
}
