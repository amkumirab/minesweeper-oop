package minesweeper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import minesweeper.ui.ConsoleUI;

/** Tests the console session's input boundary using real input streams. */
public final class ConsoleInputTests {
    private ConsoleInputTests() {}

    public static void main(String[] args) {
        String setup = "Player\n1\nn\nn\nn\nn\n";
        StringBuilder replay = new StringBuilder("Player\n4\n5\n5\n1\nn\nn\nn\nn\n");
        for (int row = 1; row <= 5; row++) {
            for (int col = 1; col <= 5; col++) replay.append("reveal ").append(row).append(' ').append(col).append('\n');
        }
        List<Case> cases = List.of(
                new Case("EOF at the player name exits cleanly", "", true, "Enter your name"),
                new Case("EOF at difficulty exits cleanly", "Player\n", true, "Choice [1-4]"),
                new Case("EOF after invalid numeric input exits cleanly", "Player\nwrong\n", true, "Invalid input"),
                new Case("EOF at a yes/no prompt exits cleanly", "Player\n1\n", true, "Enable trap cells"),
                new Case("EOF at a feature count exits cleanly", "Player\n1\ny\n", true, "Number of traps"),
                new Case("EOF at custom settings exits cleanly", "Player\n4\n", true, "Rows [5-30]"),
                new Case("EOF during gameplay exits cleanly", setup, true, "Game started!"),
                new Case("EOF after invalid gameplay input exits cleanly", setup + "reveal x y\n", true, "Coordinates must be integers"),
                new Case("EOF at replay exits cleanly", replay.toString(), true, "Play again?"),
                new Case("Quit still exits normally", setup + "quit\n", false, "Thanks for playing!"));
        int failures = 0;
        for (Case test : cases) {
            try {
                check(test);
                System.out.println("PASS: " + test.name());
            } catch (Exception | AssertionError error) {
                failures++;
                System.err.println("FAIL: " + test.name() + " - " + error);
            }
        }
        System.out.println((cases.size() - failures) + "/" + cases.size() + " console input tests passed.");
        if (failures != 0) throw new AssertionError("Console input regressions: " + failures);
    }

    private static void check(Case test) {
        InputStream originalInput = System.in;
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setIn(new ByteArrayInputStream(test.input().getBytes(StandardCharsets.UTF_8)));
            System.setOut(captured);
            new ConsoleUI().start();
        } finally {
            System.setIn(originalInput);
            System.setOut(originalOutput);
        }
        String text = output.toString(StandardCharsets.UTF_8);
        if (!text.contains(test.prompt())) throw new AssertionError("The intended input stage was not reached");
        if (text.contains("Input closed.") != test.eof()) throw new AssertionError("Unexpected EOF feedback");
    }

    private record Case(String name, String input, boolean eof, String prompt) {}
}
