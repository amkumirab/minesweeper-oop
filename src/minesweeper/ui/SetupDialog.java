package minesweeper.ui;

import minesweeper.engine.GameSettings;
import minesweeper.exceptions.InvalidGameSettingsException;

import javax.swing.*;
import java.awt.*;

/**
 * A modal dialog that lets the player choose game settings before starting.
 * Provides preset buttons (Beginner / Intermediate / Expert) plus a Custom tab.
 */
public class SetupDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private GameSettings result = null;

    // Custom fields
    private final JSpinner rowsSpinner     = new JSpinner(new SpinnerNumberModel(10, 5, 30, 1));
    private final JSpinner colsSpinner     = new JSpinner(new SpinnerNumberModel(10, 5, 30, 1));
    private final JSpinner minesSpinner    = new JSpinner(new SpinnerNumberModel(15, 1, 200, 1));
    private final JCheckBox trapsCheck     = new JCheckBox("Enable Traps");
    private final JSpinner trapsSpinner    = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
    private final JCheckBox bonusCheck     = new JCheckBox("Enable Bonuses");
    private final JSpinner bonusSpinner    = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
    private final JCheckBox livesCheck     = new JCheckBox("Enable Lives");
    private final JSpinner livesSpinner    = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
    private final JCheckBox timerCheck     = new JCheckBox("Enable Time Limit");
    private final JSpinner timerSpinner    = new JSpinner(new SpinnerNumberModel(120, 30, 600, 10));

    public SetupDialog(Frame parent) {
        super(parent, "Minesweeper — New Game", true);
        setResizable(false);
        buildUI();
        pack();
        setLocationRelativeTo(parent);
    }

    /** Returns the chosen settings, or null if the dialog was closed. */
    public GameSettings getResult() { return result; }

    // ── UI construction ─────────────────────────────────────────────────────

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // ── Title ───────────────────────────────────────────────────────────
        JLabel title = new JLabel("💣  Minesweeper  💣", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        root.add(title, BorderLayout.NORTH);

        // ── Preset buttons ──────────────────────────────────────────────────
        JPanel presets = new JPanel(new GridLayout(1, 3, 8, 0));
        JButton beginner     = preset("Beginner",     "9×9, 10 mines");
        JButton intermediate = preset("Intermediate", "16×16, 40 mines");
        JButton expert       = preset("Expert",       "16×30, 99 mines");

        beginner.addActionListener(e -> startPreset("beginner"));
        intermediate.addActionListener(e -> startPreset("intermediate"));
        expert.addActionListener(e -> startPreset("expert"));

        presets.add(beginner);
        presets.add(intermediate);
        presets.add(expert);

        // ── Custom panel ────────────────────────────────────────────────────
        JPanel custom = new JPanel();
        custom.setLayout(new BoxLayout(custom, BoxLayout.Y_AXIS));
        custom.setBorder(BorderFactory.createTitledBorder("Custom Game"));

        custom.add(row("Rows:", rowsSpinner));
        custom.add(row("Columns:", colsSpinner));
        custom.add(row("Mines:", minesSpinner));
        custom.add(Box.createVerticalStrut(6));
        custom.add(checkRow(trapsCheck, "Count:", trapsSpinner));
        custom.add(checkRow(bonusCheck, "Count:", bonusSpinner));
        custom.add(checkRow(livesCheck, "Lives:", livesSpinner));
        custom.add(checkRow(timerCheck, "Seconds:", timerSpinner));
        custom.add(Box.createVerticalStrut(8));

        trapsSpinner.setEnabled(false);
        bonusSpinner.setEnabled(false);
        livesSpinner.setEnabled(false);
        timerSpinner.setEnabled(false);
        trapsCheck.addActionListener(e -> trapsSpinner.setEnabled(trapsCheck.isSelected()));
        bonusCheck.addActionListener(e -> bonusSpinner.setEnabled(bonusCheck.isSelected()));
        livesCheck.addActionListener(e -> livesSpinner.setEnabled(livesCheck.isSelected()));
        timerCheck.addActionListener(e -> timerSpinner.setEnabled(timerCheck.isSelected()));

        JButton startCustom = new JButton("▶  Start Custom Game");
        startCustom.setFont(new Font("SansSerif", Font.BOLD, 14));
        startCustom.setAlignmentX(Component.CENTER_ALIGNMENT);
        startCustom.addActionListener(e -> startCustom());
        custom.add(startCustom);

        // ── Assemble ────────────────────────────────────────────────────────
        JPanel centre = new JPanel(new BorderLayout(0, 10));
        centre.add(presets, BorderLayout.NORTH);
        centre.add(custom, BorderLayout.CENTER);
        root.add(centre, BorderLayout.CENTER);

        setContentPane(root);
    }

    private JButton preset(String name, String desc) {
        JButton btn = new JButton("<html><center><b>" + name + "</b><br><small>" + desc + "</small></center></html>");
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        return btn;
    }

    private JPanel row(String label, JComponent comp) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.add(new JLabel(label));
        p.add(comp);
        return p;
    }

    private JPanel checkRow(JCheckBox check, String label, JSpinner spinner) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        p.add(check);
        p.add(new JLabel(label));
        p.add(spinner);
        return p;
    }

    // ── actions ─────────────────────────────────────────────────────────────

    private void startPreset(String level) {
        try {
            result = switch (level) {
                case "beginner"     -> GameSettings.beginner();
                case "intermediate" -> GameSettings.intermediate();
                case "expert"       -> GameSettings.expert();
                default             -> GameSettings.beginner();
            };
            dispose();
        } catch (InvalidGameSettingsException ex) {
            showError(ex.getMessage());
        }
    }

    private void startCustom() {
        try {
            GameSettings.Builder b = new GameSettings.Builder()
                    .rows((int) rowsSpinner.getValue())
                    .cols((int) colsSpinner.getValue())
                    .mineCount((int) minesSpinner.getValue())
                    .trapsEnabled(trapsCheck.isSelected())
                    .trapCount((int) trapsSpinner.getValue())
                    .bonusesEnabled(bonusCheck.isSelected())
                    .bonusCount((int) bonusSpinner.getValue())
                    .livesEnabled(livesCheck.isSelected())
                    .maxLives((int) livesSpinner.getValue())
                    .timeLimitEnabled(timerCheck.isSelected())
                    .timeLimitSeconds((int) timerSpinner.getValue());

            result = b.build();
            dispose();
        } catch (InvalidGameSettingsException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Invalid Settings", JOptionPane.ERROR_MESSAGE);
    }
}
