package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.openide.util.NbBundle;

import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.BrowserUtils;

/**
 * Onboarding bubble shown when no harness is configured. Lists every catalog
 * harness with its icon and detection status; found harnesses can be used
 * directly or reinstalled, missing ones offer agent-specific install
 * instructions (per-OS command with a copy button).
 */
class OnboardingBubble extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Callback for "Use this agent": receives the harness id and the found path. */
    interface SelectionCallback {
        void onUse(String harnessId, String path);
    }

    /** Delay before the "Copied" label reverts (milliseconds). */
    private static final int COPY_REVERT_MS = 1500;

    private final SelectionCallback selectionCallback;
    private final RestartCallback restartCallback;
    private final Map<String, BinaryResolver.FoundBinary> foundById = new HashMap<>();
    private final Map<String, JPanel> installPanels = new HashMap<>();
    private final JPanel rowsPanel;
    private final JPanel buttonsPanel;
    private final String osInstallCommandTemplate;

    /**
     * @param found harness binaries detected on this system (may be empty)
     * @param selectionCallback invoked when the user picks a found harness
     * @param restartCallback invoked by the Restart button
     */
    OnboardingBubble(List<BinaryResolver.FoundBinary> found,
            SelectionCallback selectionCallback, RestartCallback restartCallback) {
        this.selectionCallback = selectionCallback;
        this.restartCallback = restartCallback;
        for (BinaryResolver.FoundBinary fb : found) {
            foundById.put(fb.harnessId(), fb);
        }
        osInstallCommandTemplate = osName();

        setLayout(new BorderLayout());
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        ColorTheme theme = ThemeManager.getCurrentTheme();
        JPanel content = UIUtils.createBubbleContentPanel();

        // Header
        JLabel titleLabel = new JLabel(
                NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Title"));
        titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD,
                ThemeManager.getFont().getSize() + 4f));
        titleLabel.setForeground(theme.foreground());
        content.add(titleLabel, BorderLayout.NORTH);

        // Body text
        String text = NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Body");
        JTextAreaNoWrap body = new JTextAreaNoWrap(text);
        content.add(body, BorderLayout.CENTER);

        // One row per catalog harness
        rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setOpaque(false);
        for (HarnessCatalog.Harness harness : HarnessCatalog.ALL) {
            rowsPanel.add(createHarnessRow(harness, theme));
            rowsPanel.add(Box.createVerticalStrut(4));
        }
        content.add(rowsPanel, BorderLayout.SOUTH);

        // Restart button
        buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonsPanel.setOpaque(false);
        JButton restartBtn = new JButton(
                NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Button.Restart"));
        restartBtn.setFocusPainted(false);
        restartBtn.addActionListener(e -> {
            if (restartCallback != null) restartCallback.onRestart(this::disableButtons);
        });
        buttonsPanel.add(restartBtn);

        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setOpaque(false);
        outer.add(content, BorderLayout.NORTH);
        outer.add(buttonsPanel, BorderLayout.SOUTH);
        add(outer, BorderLayout.CENTER);
    }

    /** Builds one harness row: icon, name + status, and action buttons. */
    private JPanel createHarnessRow(HarnessCatalog.Harness harness, ColorTheme theme) {
        BinaryResolver.FoundBinary foundBinary = foundById.get(harness.id());

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Icon (theme-aware; falls back to the plugin logo)
        Icon icon = ThemeManager.getIcon(harness.iconBase() + ".svg", 20);
        if (icon == null) {
            icon = ThemeManager.getIcon("logo.svg", 20);
        }
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setVerticalAlignment(JLabel.TOP);

        // Name + status
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        JLabel nameLabel = new JLabel(harness.displayName());
        nameLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(theme.foreground());
        String statusKey = foundBinary != null ? "OnboardingBubble.Status.Found" : "OnboardingBubble.Status.Missing";
        String statusArg = foundBinary != null ? foundBinary.path()
                : NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Status.MissingValue");
        JLabel statusLabel = new JLabel(NbBundle.getMessage(
                OnboardingBubble.class, statusKey, statusArg));
        statusLabel.setFont(IconResourceManager.getMonospaceFont().deriveFont(11f));
        statusLabel.setForeground(theme.mutedForeground());
        textPanel.add(nameLabel);
        textPanel.add(statusLabel);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setOpaque(false);
        boolean found = foundBinary != null;
        JButton installBtn = new JButton(NbBundle.getMessage(OnboardingBubble.class,
                found ? "OnboardingBubble.Button.Reinstall" : "OnboardingBubble.Button.Install"));
        installBtn.setFocusPainted(false);
        JButton useBtn = null;
        if (found) {
            useBtn = new JButton(NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Button.Use"));
            useBtn.setFocusPainted(false);
            String path = foundBinary.path();
            String id = harness.id();
            useBtn.addActionListener(e -> {
                disableButtons();
                if (selectionCallback != null) selectionCallback.onUse(id, path);
            });
            btnPanel.add(useBtn);
        }

        JPanel installPanel = createInstallPanel(harness, theme);
        installPanels.put(harness.id(), installPanel);
        installPanel.setVisible(false);
        installBtn.addActionListener(e -> {
            boolean showing = installPanel.isVisible();
            hideAllInstallPanels();
            installPanel.setVisible(!showing);
            revalidate();
            repaint();
        });
        btnPanel.add(installBtn);

        row.add(iconLabel, BorderLayout.WEST);
        row.add(textPanel, BorderLayout.CENTER);
        row.add(btnPanel, BorderLayout.EAST);

        // Install instructions (hidden by default) stack below the row
        JPanel cell = new JPanel();
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setOpaque(false);
        cell.setAlignmentX(Component.LEFT_ALIGNMENT);
        cell.add(row);
        cell.add(installPanel);
        return cell;
    }

    /** Per-OS install command + copy button + prerequisites note + docs link. */
    private JPanel createInstallPanel(HarnessCatalog.Harness harness, ColorTheme theme) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 28, 4, 0));

        String cmd = switch (osInstallCommandTemplate) {
            case "win" -> harness.installWindows();
            case "mac" -> harness.installMac();
            default -> harness.installLinux();
        };

        // Command line with copy-on-click
        Color cmdBg = theme.isDark() ? new Color(0x1A1B26) : new Color(0xF0F0F0);
        Color cmdFg = theme.isDark() ? new Color(0xA1EFE4) : new Color(0x333333);
        JLabel cmdLabel = new JLabel("$ " + cmd);
        cmdLabel.setFont(IconResourceManager.getMonospaceFont());
        cmdLabel.setForeground(cmdFg);
        cmdLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cmdLabel.setToolTipText(NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Hint.Copy"));
        JPanel cmdPanel = new JPanel(new BorderLayout());
        cmdPanel.setOpaque(true);
        cmdPanel.setBackground(cmdBg);
        cmdPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        cmdPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        cmdPanel.add(cmdLabel, BorderLayout.CENTER);
        cmdPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cmdPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                copyCommand(cmdLabel, cmd);
            }
        });
        panel.add(cmdPanel);

        if (!harness.prerequisites().isBlank()) {
            JTextAreaNoWrap prereq = new JTextAreaNoWrap(harness.prerequisites(), 10f);
            prereq.setForeground(theme.mutedForeground());
            panel.add(prereq);
        }

        JButton docsBtn = new JButton(NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.Button.Docs"));
        docsBtn.setFocusPainted(false);
        docsBtn.addActionListener(e ->
                BrowserUtils.openOrCopyUrl(harness.docsUrl(), null, null));
        JPanel docsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        docsRow.setOpaque(false);
        docsRow.add(docsBtn);
        panel.add(docsRow);
        return panel;
    }

    /** Collapses every open install panel. */
    private void hideAllInstallPanels() {
        for (JPanel p : installPanels.values()) {
            p.setVisible(false);
        }
    }

    /** Copies the command and flashes the label text to confirm. */
    private void copyCommand(JLabel label, String command) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(command), null);
        String previous = label.getText();
        label.setText(NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Copied"));
        Timer revert = new Timer(COPY_REVERT_MS, e -> label.setText(previous));
        revert.setRepeats(false);
        revert.start();
    }

    /** Disables all action buttons so the user cannot trigger actions twice. */
    private void disableButtons() {
        if (buttonsPanel != null) {
            for (Component c : buttonsPanel.getComponents()) {
                c.setEnabled(false);
            }
        }
        setAllRowButtonsEnabled(false);
    }

    private void setAllRowButtonsEnabled(boolean enabled) {
        for (JPanel installPanel : installPanels.values()) {
            installPanel.setVisible(false);
        }
        setButtonsEnabledRecursive(rowsPanel, enabled);
    }

    private static void setButtonsEnabledRecursive(Component c, boolean enabled) {
        if (c instanceof JButton btn) {
            btn.setEnabled(enabled);
        } else if (c instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                setButtonsEnabledRecursive(child, enabled);
            }
        }
    }

    private static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "win";
        }
        if (os.contains("mac")) {
            return "mac";
        }
        return "linux";
    }

    /** Non-wrapping small JTextArea used for body/prerequisite text. */
    private static final class JTextAreaNoWrap extends javax.swing.JTextArea {
        private static final long serialVersionUID = 1L;

        JTextAreaNoWrap(String text) {
            this(text, ThemeManager.getFont().getSize() + 1f);
        }

        JTextAreaNoWrap(String text, float fontSize) {
            super(text);
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setOpaque(false);
            setFocusable(false);
            setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, fontSize));
            setForeground(ThemeManager.getCurrentTheme().foreground());
        }
    }
}
