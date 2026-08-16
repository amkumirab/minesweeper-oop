package minesweeper.engine;

/**
 * Tracks elapsed time and enforces an optional countdown limit.
 *
 * Encapsulation: raw timestamps are private; consumers use only formatted strings
 * and boolean checks.
 */
public class GameTimer {

    private final int  limitSeconds;      // 0 means no limit
    private       long startEpochMs;
    private       long stoppedAtMs;       // -1 while running
    private       boolean running;

    // ── constructor ──────────────────────────────────────────────────────────

    public GameTimer(int limitSeconds) {
        this.limitSeconds = limitSeconds;
        this.running      = false;
        this.stoppedAtMs  = -1;
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    public void start() {
        startEpochMs = System.currentTimeMillis();
        stoppedAtMs  = -1;
        running      = true;
    }

    public void stop() {
        if (running) {
            stoppedAtMs = System.currentTimeMillis();
            running     = false;
        }
    }

    // ── queries ──────────────────────────────────────────────────────────────

    public long getElapsedSeconds() {
        if (!running && stoppedAtMs < 0) return 0;
        long endMs = running ? System.currentTimeMillis() : stoppedAtMs;
        return (endMs - startEpochMs) / 1_000L;
    }

    public long getRemainingSeconds() {
        if (limitSeconds <= 0) return Long.MAX_VALUE;
        return Math.max(0, limitSeconds - getElapsedSeconds());
    }

    public boolean isTimeUp() {
        return running && limitSeconds > 0 && getElapsedSeconds() >= limitSeconds;
    }

    public boolean isRunning() { return running; }
    public int     getLimit()  { return limitSeconds; }

    // ── formatting ───────────────────────────────────────────────────────────

    public String getFormattedElapsed() {
        return format(getElapsedSeconds());
    }

    public String getFormattedRemaining() {
        long rem = getRemainingSeconds();
        return rem == Long.MAX_VALUE ? "∞" : format(rem);
    }

    private static String format(long totalSeconds) {
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
