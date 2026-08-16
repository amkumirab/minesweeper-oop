package minesweeper.ui;

import minesweeper.engine.*;
import minesweeper.exceptions.*;
import minesweeper.model.Board;
import minesweeper.model.Player;
import minesweeper.model.cell.*;

import java.util.Scanner;

/**
 * Console-based user interface.
 *
 * Responsibilities (single responsibility):
 *  - Collect and validate raw user input
 *  - Delegate all game logic to GameEngine
 *  - Render the board and status line to stdout
 *
 * The UI knows nothing about how mines are placed or how flood-fill works —
 * that is purely the engine's concern (modularity / separation of concerns).
 */
public class ConsoleUI {

    // ── ANSI colour codes (gracefully ignored on terminals that don't support them) ──
    private static final String RESET   = "\u001B[0m";
    private static final String RED     = "\u001B[31m";
    private static final String GREEN   = "\u001B[32m";
    private static final String YELLOW  = "\u001B[33m";
    private static final String BLUE    = "\u001B[34m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String CYAN    = "\u001B[36m";
    private static final String BOLD    = "\u001B[1m";

    private static final String[] DIGIT_COLOURS = {
        RESET,   // 0 — unused
        BLUE,    // 1
        GREEN,   // 2
        RED,     // 3
        MAGENTA, // 4
        RED,     // 5
        CYAN,    // 6
        BOLD,    // 7
        RED      // 8
    };

    // ── state ────────────────────────────────────────────────────────────────
    private final Scanner    scanner;
    private       GameEngine engine;

    // ── constructor ──────────────────────────────────────────────────────────

    public ConsoleUI() {
        this.scanner = new Scanner(System.in);
    }

    // ── entry point ───────────────────────────────────────────────────────────

    public void start() {
        printBanner();

        boolean playAgain;
        do {
            String playerName = promptPlayerName();
            GameSettings settings = promptSettings();

            engine = new GameEngine(settings, playerName);
            engine.initialise();

            playAgain = runGameLoop();
        } while (playAgain);
    }

    // ── setup screens ─────────────────────────────────────────────────────────

    private void printBanner() {
        System.out.println();
        System.out.println(BOLD + "╔══════════════════════════════════════════╗" + RESET);
        System.out.println(BOLD + "║  💣  MINESWEEPER — EXTENDED EDITION  💣  ║" + RESET);
        System.out.println(BOLD + "╚══════════════════════════════════════════╝" + RESET);
        System.out.println();
    }

    private String promptPlayerName() {
        System.out.print("Enter your name: ");
        String name = scanner.nextLine().trim();
        return name.isEmpty() ? "Player" : name;
    }

    private GameSettings promptSettings() {
        System.out.println();
        System.out.println(BOLD + "── Difficulty ──────────────────────────────" + RESET);
        System.out.println("  1. Beginner       (9×9,  10 mines)");
        System.out.println("  2. Intermediate  (16×16, 40 mines)");
        System.out.println("  3. Expert        (16×30, 99 mines)");
        System.out.println("  4. Custom");

        int diff = readInt("Choice [1-4]: ", 1, 4);

        GameSettings.Builder builder = new GameSettings.Builder();

        switch (diff) {
            case 1 -> builder.rows(9).cols(9).mineCount(10);
            case 2 -> builder.rows(16).cols(16).mineCount(40);
            case 3 -> builder.rows(16).cols(30).mineCount(99);
            case 4 -> configureCustom(builder);
        }

        System.out.println();
        System.out.println(BOLD + "── Extended Features ───────────────────────" + RESET);

        // Traps
        if (readYesNo("Enable trap cells? [y/n]: ")) {
            int tc = readInt("  Number of traps [1-10]: ", 1, 10);
            builder.trapsEnabled(true).trapCount(tc);
        }

        // Bonuses
        if (readYesNo("Enable bonus cells? [y/n]: ")) {
            int bc = readInt("  Number of bonuses [1-5]: ", 1, 5);
            builder.bonusesEnabled(true).bonusCount(bc);
        }

        // Lives
        if (readYesNo("Enable lives system? [y/n]: ")) {
            int lv = readInt("  Number of lives [1-5]: ", 1, 5);
            builder.livesEnabled(true).maxLives(lv);
        }

        // Time limit
        if (readYesNo("Enable time limit? [y/n]: ")) {
            int secs = readInt("  Time limit in seconds [30-600]: ", 30, 600);
            builder.timeLimitEnabled(true).timeLimitSeconds(secs);
        }

        try {
            return builder.build();
        } catch (InvalidGameSettingsException e) {
            System.out.println(YELLOW + "⚠ " + e.getMessage() + " — using default settings." + RESET);
            try {
                return new GameSettings.Builder().build();
            } catch (InvalidGameSettingsException ex) {
                throw new RuntimeException("Cannot build default settings", ex);
            }
        }
    }

    private void configureCustom(GameSettings.Builder builder) {
        int r = readInt("  Rows [5-30]: ", 5, 30);
        int c = readInt("  Cols [5-30]: ", 5, 30);
        int maxMines = Math.max(1, (r * c) / 4);
        int m = readInt("  Mines [1-" + maxMines + "]: ", 1, maxMines);
        builder.rows(r).cols(c).mineCount(m);
    }

    // ── main game loop ────────────────────────────────────────────────────────

    private boolean runGameLoop() {
        System.out.println();
        System.out.println(GREEN + "🎮 Game started!  Good luck!" + RESET);
        printHelp();
        System.out.println();

        printBoard();

        while (true) {
            printStatusBar();
            System.out.print(BOLD + "\n> " + RESET);
            String raw = scanner.nextLine().trim();

            if (raw.isEmpty()) continue;

            String lower = raw.toLowerCase();

            if (lower.equals("q") || lower.equals("quit") || lower.equals("exit")) {
                System.out.println("Thanks for playing! 👋");
                return false;
            }
            if (lower.equals("help") || lower.equals("h") || lower.equals("?")) {
                printHelp();
                continue;
            }
            if (lower.equals("undo")) {
                if (handleUndo()) return offerReplay();
                continue;
            }

            String[] parts = lower.split("\\s+");
            if (parts.length < 3) {
                System.out.println(YELLOW + "⚠ Usage:  reveal <row> <col>  |  flag <row> <col>" + RESET);
                continue;
            }

            String cmd = parts[0];
            try {
                // Convert from 1-indexed display to 0-indexed internal
                int row = Integer.parseInt(parts[1]) - 1;
                int col = Integer.parseInt(parts[2]) - 1;

                switch (cmd) {
                    case "reveal", "r", "open", "o" -> {
                        if (handleReveal(row, col)) return offerReplay();
                    }
                    case "flag",   "f"               -> handleFlag(row, col);
                    default -> System.out.println(YELLOW + "⚠ Unknown command '" + cmd
                            + "'. Type 'help' for instructions." + RESET);
                }
            } catch (NumberFormatException e) {
                System.out.println(YELLOW + "⚠ Coordinates must be integers." + RESET);
            }
        }
    }

    // ── action handlers ───────────────────────────────────────────────────────

    private boolean handleReveal(int row, int col) {
        try {
            RevealResult result = engine.revealCell(row, col);
            printResultMessage(result);
            printBoard();
        } catch (GameOverException e) {
            printBoard();
            if (e.isWon()) {
                System.out.println(GREEN + BOLD + "\n🏆  " + e.getMessage() + RESET);
                printWinSummary();
            } else {
                System.out.println(RED + BOLD + "\n💥  " + e.getMessage() + RESET);
                printLossSummary();
            }
            return true;
        } catch (InvalidCoordinateException e) {
            System.out.println(YELLOW + "⚠ " + e.getMessage() + RESET);
        } catch (CellAlreadyRevealedException e) {
            System.out.println(YELLOW + "⚠ " + e.getMessage() + RESET);
        }
        return false;
    }

    private void handleFlag(int row, int col) {
        try {
            engine.toggleFlag(row, col);
            System.out.println("🚩 Flag toggled at (" + (row + 1) + ", " + (col + 1) + ").");
            printBoard();
        } catch (InvalidCoordinateException e) {
            System.out.println(YELLOW + "⚠ " + e.getMessage() + RESET);
        }
    }

    private boolean handleUndo() {
        try {
            boolean used = engine.useUndo();
            if (used) {
                System.out.println(CYAN + "↩ Undo used! A safe cell has been revealed for you." + RESET);
                printBoard();
            } else {
                System.out.println(YELLOW + "⚠ No undo token available. Collect a 🎁 Bonus to get one." + RESET);
            }
        } catch (GameOverException e) {
            printBoard();
            if (e.isWon()) {
                System.out.println(GREEN + BOLD + "\n🏆  " + e.getMessage() + RESET);
                printWinSummary();
            } else {
                System.out.println(RED + BOLD + "\n💥  " + e.getMessage() + RESET);
                printLossSummary();
            }
            return true;
        }
        return false;
    }

    // ── board rendering ───────────────────────────────────────────────────────

    private void printBoard() {
        Board<Cell> board    = engine.getBoard();
        if (board == null) return;

        int rows = board.getRows();
        int cols = board.getCols();

        System.out.println();

        // Column header
        System.out.print("     ");
        for (int c = 0; c < cols; c++) {
            System.out.printf("%3d", c + 1);
        }
        System.out.println();

        // Top border
        System.out.print("    +");
        System.out.print("---".repeat(cols));
        System.out.println("+");

        // Rows
        for (int r = 0; r < rows; r++) {
            System.out.printf("%3d |", r + 1);
            for (int c = 0; c < cols; c++) {
                try {
                    Cell cell = board.getCell(r, c);
                    System.out.print(" " + styledCell(cell) + " ");
                } catch (InvalidCoordinateException e) {
                    System.out.print(" ? ");
                }
            }
            System.out.println("|");
        }

        // Bottom border
        System.out.print("    +");
        System.out.print("---".repeat(cols));
        System.out.println("+");
    }

    private String styledCell(Cell cell) {
        if (cell == null) return "·";   // safety: cell not yet initialised
        if (cell.isFlagged() && !cell.isRevealed()) return YELLOW + "F" + RESET;
        if (!cell.isRevealed())                      return "·";

        if (cell instanceof MineCell)  return RED  + "*" + RESET;
        if (cell instanceof TrapCell)  return RED  + "T" + RESET;
        if (cell instanceof BonusCell) return GREEN + "B" + RESET;

        if (cell instanceof NormalCell nc) {
            int d = nc.getAdjacentDanger();
            if (d == 0) return " ";
            String colour = (d >= 1 && d <= 8) ? DIGIT_COLOURS[d] : RESET;
            return colour + d + RESET;
        }
        return "?";
    }

    // ── status bar ────────────────────────────────────────────────────────────

    private void printStatusBar() {
        Player       player   = engine.getPlayer();
        GameSettings settings = engine.getSettings();
        GameTimer    timer    = engine.getTimer();

        StringBuilder sb = new StringBuilder();
        sb.append(BOLD).append("── ").append(player.getName()).append(RESET);
        sb.append("  |  💣 ").append(engine.getRemainingMineCount()).append(" mines");
        sb.append("  |  Score: ").append(player.getScore());

        if (settings.isLivesEnabled()) {
            sb.append("  |  ❤ ").append(player.getLives()).append("/")
              .append(settings.getMaxLives());
        }
        if (timer != null && timer.isRunning()) {
            if (settings.isTimeLimitEnabled()) {
                long rem = timer.getRemainingSeconds();
                String colour = rem <= 30 ? RED : (rem <= 60 ? YELLOW : GREEN);
                sb.append("  |  ⏱ ").append(colour).append(timer.getFormattedRemaining()).append(RESET);
            } else {
                sb.append("  |  ⏱ ").append(timer.getFormattedElapsed());
            }
        }
        if (player.isUndoAvailable()) sb.append("  |  ↩ UNDO READY");

        System.out.println(sb);
    }

    // ── result & summary ──────────────────────────────────────────────────────

    private void printResultMessage(RevealResult result) {
        String msg = result.getMessage();
        switch (result.getOutcome()) {
            case MINE    -> System.out.println(RED    + "  " + msg + RESET);
            case TRAP    -> System.out.println(RED    + "  " + msg + RESET);
            case BONUS   -> System.out.println(GREEN  + "  " + msg + RESET);
            case FROZEN  -> System.out.println(CYAN   + "  " + msg + RESET);
            case FLAGGED -> System.out.println(YELLOW + "  " + msg + RESET);
            default      -> {}   // normal reveals are silent
        }
    }

    private void printWinSummary() {
        Player    player = engine.getPlayer();
        GameTimer timer  = engine.getTimer();
        System.out.println();
        System.out.println("  Final score : " + player.getScore());
        if (timer != null) System.out.println("  Time taken  : " + timer.getFormattedElapsed());
        if (engine.getSettings().isLivesEnabled())
            System.out.println("  Lives left  : " + player.getLives());
    }

    private void printLossSummary() {
        Player    player = engine.getPlayer();
        GameTimer timer  = engine.getTimer();
        System.out.println();
        System.out.println("  Score so far : " + player.getScore());
        if (timer != null) System.out.println("  Survived for : " + timer.getFormattedElapsed());
    }

    private boolean offerReplay() {
        System.out.println();
        boolean replay = readYesNo("Play again? [y/n]: ");
        if (!replay) {
            System.out.println("Thanks for playing! 👋");
        }
        return replay;
    }

    // ── help screen ──────────────────────────────────────────────────────────

    private void printHelp() {
        System.out.println();
        System.out.println(BOLD + "── Commands ────────────────────────────────" + RESET);
        System.out.println("  reveal <row> <col>   Reveal a cell   (aliases: r, open, o)");
        System.out.println("  flag   <row> <col>   Toggle flag     (alias:  f)");
        System.out.println("  undo                 Use undo token  (if available)");
        System.out.println("  help                 Show this help  (aliases: h, ?)");
        System.out.println("  quit                 Exit the game   (alias:  q)");
        System.out.println();
        System.out.println(BOLD + "── Board Symbols ───────────────────────────" + RESET);
        System.out.println("  ·   Hidden cell");
        System.out.println("  F   Flagged cell (protected from accidental reveal)");
        System.out.println("  1-8 Number of adjacent mines + traps");
        System.out.println("  (space) Empty — no dangerous neighbours");
        System.out.println("  *   Mine  (revealed after game ends)");
        System.out.println("  T   Trap  (revealed after game ends)");
        System.out.println("  B   Bonus cell collected");
        System.out.println();
        System.out.println(BOLD + "── Tips ─────────────────────────────────────" + RESET);
        System.out.println("  • Coordinates are 1-indexed (top-left is row 1, col 1).");
        System.out.println("  • Flag all suspected mines before revealing adjacent cells.");
        System.out.println("  • Bonus cells grant: extra life | safe reveal | undo token.");
        System.out.println("  • Trap effects: lose life | expose a mine | freeze next move.");
    }

    // ── input helpers ─────────────────────────────────────────────────────────

    /**
     * Reads an integer in [min, max] from stdin, re-prompting on bad input.
     * Demonstrates <b>overloading</b> — could also call readInt(prompt) without bounds.
     */
    private int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int v = Integer.parseInt(scanner.nextLine().trim());
                if (v >= min && v <= max) return v;
                System.out.println(YELLOW + "  Please enter a number between "
                        + min + " and " + max + "." + RESET);
            } catch (NumberFormatException e) {
                System.out.println(YELLOW + "  Invalid input — please enter a number." + RESET);
            }
        }
    }

    private boolean readYesNo(String prompt) {
        while (true) {
            System.out.print(prompt);
            String ans = scanner.nextLine().trim().toLowerCase();
            if (ans.equals("y") || ans.equals("yes")) return true;
            if (ans.equals("n") || ans.equals("no"))  return false;
            System.out.println(YELLOW + "  Please enter 'y' or 'n'." + RESET);
        }
    }
}
