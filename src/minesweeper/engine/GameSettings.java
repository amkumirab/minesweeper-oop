package minesweeper.engine;

import minesweeper.exceptions.InvalidGameSettingsException;

/**
 * Immutable value object holding all configuration for a game session.
 *
 * Builder pattern (a form of composition) separates construction from representation
 * and makes invalid states impossible to construct (exception at build-time).
 *
 * Encapsulation: all fields are private and final; set only through the Builder.
 */
public final class GameSettings {

    // ── fields ───────────────────────────────────────────────────────────────

    private final int     rows;
    private final int     cols;
    private final int     mineCount;
    private final boolean trapsEnabled;
    private final int     trapCount;
    private final boolean bonusesEnabled;
    private final int     bonusCount;
    private final boolean livesEnabled;
    private final int     maxLives;
    private final boolean timeLimitEnabled;
    private final int     timeLimitSeconds;

    // ── private constructor (only the Builder may call it) ────────────────────

    private GameSettings(Builder b) {
        this.rows              = b.rows;
        this.cols              = b.cols;
        this.mineCount         = b.mineCount;
        this.trapsEnabled      = b.trapsEnabled;
        this.trapCount         = b.trapCount;
        this.bonusesEnabled    = b.bonusesEnabled;
        this.bonusCount        = b.bonusCount;
        this.livesEnabled      = b.livesEnabled;
        this.maxLives          = b.maxLives;
        this.timeLimitEnabled  = b.timeLimitEnabled;
        this.timeLimitSeconds  = b.timeLimitSeconds;
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public int     getRows()             { return rows;             }
    public int     getCols()             { return cols;             }
    public int     getMineCount()        { return mineCount;        }
    public boolean isTrapsEnabled()      { return trapsEnabled;     }
    public int     getTrapCount()        { return trapCount;        }
    public boolean isBonusesEnabled()    { return bonusesEnabled;   }
    public int     getBonusCount()       { return bonusCount;       }
    public boolean isLivesEnabled()      { return livesEnabled;     }
    public int     getMaxLives()         { return maxLives;         }
    public boolean isTimeLimitEnabled()  { return timeLimitEnabled; }
    public int     getTimeLimitSeconds() { return timeLimitSeconds; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static class Builder {

        // defaults
        private int     rows             = 10;
        private int     cols             = 10;
        private int     mineCount        = 15;
        private boolean trapsEnabled     = false;
        private int     trapCount        = 0;
        private boolean bonusesEnabled   = false;
        private int     bonusCount       = 0;
        private boolean livesEnabled     = false;
        private int     maxLives         = 3;
        private boolean timeLimitEnabled = false;
        private int     timeLimitSeconds = 120;

        public Builder rows(int v)             { this.rows             = v; return this; }
        public Builder cols(int v)             { this.cols             = v; return this; }
        public Builder mineCount(int v)        { this.mineCount        = v; return this; }
        public Builder trapsEnabled(boolean v) { this.trapsEnabled     = v; return this; }
        public Builder trapCount(int v)        { this.trapCount        = v; return this; }
        public Builder bonusesEnabled(boolean v){ this.bonusesEnabled  = v; return this; }
        public Builder bonusCount(int v)       { this.bonusCount       = v; return this; }
        public Builder livesEnabled(boolean v) { this.livesEnabled     = v; return this; }
        public Builder maxLives(int v)         { this.maxLives         = v; return this; }
        public Builder timeLimitEnabled(boolean v){ this.timeLimitEnabled = v; return this; }
        public Builder timeLimitSeconds(int v) { this.timeLimitSeconds = v; return this; }

        /**
         * Builds the settings after validating all constraints.
         *
         * @throws InvalidGameSettingsException if any rule is violated
         */
        public GameSettings build() throws InvalidGameSettingsException {
            validate();
            return new GameSettings(this);
        }

        private void validate() throws InvalidGameSettingsException {
            if (rows < 5 || rows > 30)
                throw new InvalidGameSettingsException(
                        "Board rows must be between 5 and 30, got: " + rows);
            if (cols < 5 || cols > 30)
                throw new InvalidGameSettingsException(
                        "Board cols must be between 5 and 30, got: " + cols);

            int totalCells = rows * cols;
            // Reserve a 3×3 safe zone around the first click → at most 9 cells safe
            int reservedSafe = 9;
            if (mineCount < 1)
                throw new InvalidGameSettingsException("There must be at least 1 mine.");
            if (trapsEnabled && trapCount < 1)
                throw new InvalidGameSettingsException(
                        "Trap count must be at least 1 when traps are enabled.");
            if (bonusesEnabled && bonusCount < 1)
                throw new InvalidGameSettingsException(
                        "Bonus count must be at least 1 when bonuses are enabled.");

            long special = (long) mineCount
                    + (trapsEnabled   ? trapCount  : 0)
                    + (bonusesEnabled ? bonusCount : 0);
            if (special > totalCells - reservedSafe)
                throw new InvalidGameSettingsException(
                        "Too many special cells (" + special + ") for a "
                        + rows + "×" + cols + " board.");
            if (livesEnabled && maxLives < 1)
                throw new InvalidGameSettingsException("Max lives must be at least 1.");
            if (timeLimitEnabled && timeLimitSeconds < 30)
                throw new InvalidGameSettingsException(
                        "Time limit must be at least 30 seconds.");
        }
    }

    // ── quick presets ─────────────────────────────────────────────────────────

    public static GameSettings beginner() throws InvalidGameSettingsException {
        return new Builder().rows(9).cols(9).mineCount(10).build();
    }

    public static GameSettings intermediate() throws InvalidGameSettingsException {
        return new Builder().rows(16).cols(16).mineCount(40).build();
    }

    public static GameSettings expert() throws InvalidGameSettingsException {
        return new Builder().rows(16).cols(30).mineCount(99).build();
    }
}
