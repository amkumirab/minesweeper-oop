package minesweeper.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Represents the human player.
 *
 * Encapsulation: all state is private; mutated only through controlled methods.
 * Composition  : owns a move-history stack and a debuff list.
 */
public class Player {

    private static final int MAX_HISTORY = 20;

    // ── encapsulated state ───────────────────────────────────────────────────
    private final String       name;
    private       int          lives;
    private       int          score;
    private       boolean      undoAvailable;
    private final List<String> debuffs;
    private final Deque<int[]> moveHistory;   // stack: most-recent move on top

    // ── constructor ──────────────────────────────────────────────────────────

    public Player(String name, int initialLives) {
        this.name          = name;
        this.lives         = initialLives;
        this.score         = 0;
        this.undoAvailable = false;
        this.debuffs       = new ArrayList<>();
        this.moveHistory   = new ArrayDeque<>();
    }

    // ── life management ──────────────────────────────────────────────────────

    public void loseLife() {
        if (lives > 0) lives--;
    }

    public void gainLife() { lives++; }

    public boolean isAlive() { return lives > 0; }

    // ── score ────────────────────────────────────────────────────────────────

    public void addScore(int points) {
        if (points > 0) score += points;
    }

    // ── undo token ───────────────────────────────────────────────────────────

    public boolean isUndoAvailable() { return undoAvailable; }

    public void setUndoAvailable(boolean value) { this.undoAvailable = value; }

    public boolean consumeUndo() {
        if (!undoAvailable) return false;
        undoAvailable = false;
        return true;
    }

    // ── debuffs ──────────────────────────────────────────────────────────────

    public void addDebuff(String tag)    { debuffs.add(tag); }
    public boolean hasDebuff(String tag) { return debuffs.contains(tag); }
    public void removeDebuff(String tag) { debuffs.remove(tag); }
    public List<String> getDebuffs()     { return Collections.unmodifiableList(debuffs); }

    // ── move history ─────────────────────────────────────────────────────────

    public void recordMove(int row, int col) {
        moveHistory.push(new int[]{row, col});
        if (moveHistory.size() > MAX_HISTORY) {
            // trim oldest entry — rebuild without the last element
            List<int[]> snapshot = new ArrayList<>(moveHistory);
            moveHistory.clear();
            for (int i = 0; i < snapshot.size() - 1; i++) {
                moveHistory.addLast(snapshot.get(i));
            }
        }
    }

    /** Returns the most recent move [row, col], or null if no history. */
    public int[] peekLastMove() {
        return moveHistory.isEmpty() ? null : moveHistory.peek();
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public String getName()  { return name;  }
    public int    getLives() { return lives; }
    public int    getScore() { return score; }
}
