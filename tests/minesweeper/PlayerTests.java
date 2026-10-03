package minesweeper;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import minesweeper.model.Player;

/** Dependency-free regression tests for player move history. */
public final class PlayerTests {
    private PlayerTests() {}

    public static void main(String[] args) throws Exception {
        List<TestCase> tests = List.of(
                new TestCase("Empty history returns null", PlayerTests::emptyHistory),
                new TestCase("Last move is the newest recorded move", PlayerTests::newestMove),
                new TestCase("Returned coordinates cannot change stored history", PlayerTests::defensiveCopy),
                new TestCase("Returned coordinates remain unchanged after recording", PlayerTests::stableSnapshot),
                new TestCase("History keeps all moves up to its limit", () -> boundedHistory(20)),
                new TestCase("History removes the oldest move at overflow", () -> boundedHistory(21)),
                new TestCase("Repeated overflow preserves the newest twenty moves", () -> boundedHistory(45)));
        int failures = 0;
        for (TestCase test : tests) {
            try {
                test.body().run();
                System.out.println("PASS: " + test.name());
            } catch (Exception | AssertionError error) {
                failures++;
                System.err.println("FAIL: " + test.name() + " - " + error);
            }
        }
        System.out.println((tests.size() - failures) + "/" + tests.size() + " player tests passed.");
        require(failures == 0, "Player regression tests failed: " + failures);
    }

    private static void emptyHistory() {
        Player player = new Player("Player", 3);
        require(player.peekLastMove() == null, "An empty history must return null");
    }

    private static void newestMove() {
        Player player = new Player("Player", 3);
        player.recordMove(1, 2);
        equalMove(1, 2, player.peekLastMove());
        player.recordMove(3, 4);
        equalMove(3, 4, player.peekLastMove());
    }

    private static void defensiveCopy() {
        Player player = new Player("Player", 3);
        player.recordMove(1, 2);
        int[] returnedMove = player.peekLastMove();
        returnedMove[0] = 99;
        returnedMove[1] = 88;
        equalMove(1, 2, player.peekLastMove());
        require(returnedMove != player.peekLastMove(), "Each lookup must return a separate copy");
    }

    private static void stableSnapshot() {
        Player player = new Player("Player", 3);
        player.recordMove(1, 2);
        int[] snapshot = player.peekLastMove();
        player.recordMove(3, 4);
        equalMove(1, 2, snapshot);
        equalMove(3, 4, player.peekLastMove());
    }

    private static void boundedHistory(int recordedMoves) throws ReflectiveOperationException {
        Player player = new Player("Player", 3);
        for (int move = 0; move < recordedMoves; move++) player.recordMove(move, move + 100);
        Deque<?> history = readHistory(player);
        require(history.size() == 20, "History must retain exactly twenty moves");
        int expectedRow = recordedMoves - 1;
        for (Object entry : history) {
            equalMove(expectedRow, expectedRow + 100, (int[]) entry);
            expectedRow--;
        }
        equalMove(recordedMoves - 1, recordedMoves + 99, player.peekLastMove());
    }

    // Inspect the private capacity invariant without adding a public history API.
    private static Deque<?> readHistory(Player player) throws ReflectiveOperationException {
        Field field = Player.class.getDeclaredField("moveHistory");
        field.setAccessible(true);
        return (Deque<?>) field.get(player);
    }

    private static void equalMove(int row, int col, int[] actual) {
        int[] expected = {row, col};
        require(Arrays.equals(expected, actual),
                "Expected " + Arrays.toString(expected) + ", got " + Arrays.toString(actual));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record TestCase(String name, TestBody body) {}

    @FunctionalInterface
    private interface TestBody {
        void run() throws Exception;
    }
}
