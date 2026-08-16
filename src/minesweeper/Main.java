package minesweeper;

import minesweeper.ui.SwingUI;

import javax.swing.*;

/**
 * Application entry point.
 *
 * Launches the graphical Swing UI for the Minesweeper game.
 * The console UI (ConsoleUI) is still available for headless environments.
 *
 * Usage:
 *   java minesweeper.Main          → Swing GUI (default)
 *   java minesweeper.Main --console → Console mode
 */
public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--console")) {
            new minesweeper.ui.ConsoleUI().start();
        } else {
            // Use the system look and feel for a native appearance
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            SwingUtilities.invokeLater(() -> new SwingUI());
        }
    }
}
