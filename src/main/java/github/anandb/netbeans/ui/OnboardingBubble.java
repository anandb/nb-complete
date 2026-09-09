package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.netbeans.api.options.OptionsDisplayer;
import org.openide.util.NbBundle;

import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.BrowserUtils;

/**
 * Onboarding bubble shown when no harness is configured. Lists every catalog
 * harness with its icon and detection status; found harnesses can be used
 * directly. A manual-configuration option opens the Assistant settings for
 * pointing the plugin at an arbitrary executable path.
 */
class OnboardingBubble extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Callback for "Use this harness": receives the harness id and the found path. */
    interface SelectionCallback {
        void onUse(String harnessId, String path);
    }

    private final SelectionCallback selectionCallback;
    private final RestartCallback restartCallback;
    private final java.util.List<BinaryResolver.FoundBinary> foundBinaries;
    private final JPanel rowsPanel;
    private final JPanel buttonsPanel;

    /**
     * @param found harness binaries detected on this system (may be empty)
     * @param selectionCallback invoked when the user picks a found harness
     * @param restartCallback invoked by the Restart button
     */
    OnboardingBubble(java.util.List<BinaryResolver.FoundBinary> found,
            SelectionCallback selectionCallback, RestartCallback restartCallback) {
        this.selectionCallback = selectionCallback;
        this.restartCallback = restartCallback;
        this.foundBinaries = found;

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
        body.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));
        content.add(body, BorderLayout.CENTER);

        // One row per catalog harness
        rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setOpaque(false);
        for (HarnessCatalog.Harness harness : HarnessCatalog.ALL) {
            rowsPanel.add(createHarnessRow(harness, theme));
            rowsPanel.add(Box.createVerticalStrut(18));
        }
        content.add(rowsPanel, BorderLayout.SOUTH);

        // Buttons: manual configuration (gear icon) + restart
        buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonsPanel.setOpaque(false);
        JButton manualSetupBtn = new JButton(NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.Button.ManualSetup"));
        manualSetupBtn.setFocusPainted(false);
        manualSetupBtn.setIcon(ThemeManager.getIcon("settings.svg", 16));
        manualSetupBtn.setIconTextGap(6);
        manualSetupBtn.addActionListener(e -> OptionsDisplayer.getDefault()
                .open("github-anandb-netbeans-ui-ACPOptionsPanelController"));
        JButton restartBtn = new JButton(
                NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Button.Restart"));
        restartBtn.setFocusPainted(false);
        restartBtn.addActionListener(e -> {
            if (restartCallback != null) restartCallback.onRestart(this::disableButtons);
        });
        buttonsPanel.add(manualSetupBtn);
        buttonsPanel.add(restartBtn);

        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setOpaque(false);
        outer.add(content, BorderLayout.NORTH);
        outer.add(buttonsPanel, BorderLayout.SOUTH);
        add(outer, BorderLayout.CENTER);
    }

    /** Builds one harness row: icon, name + status, and a Use button when found. */
    private JPanel createHarnessRow(HarnessCatalog.Harness harness, ColorTheme theme) {
        BinaryResolver.FoundBinary foundBinary = findFoundBinary(harness.id());

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Icon (theme-aware; falls back to the generic agent icon)
        Icon icon = ThemeManager.getIcon(harness.iconBase() + ".svg", 32);
        if (icon == null) {
            icon = ThemeManager.getIcon("agent.svg", 32);
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

        // "Use" for detected harnesses; "Install" (opens the harness's install
        // docs) for the missing ones.
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setOpaque(false);
        if (foundBinary != null) {
            JButton useBtn = new JButton(NbBundle.getMessage(
                    OnboardingBubble.class, "OnboardingBubble.Button.Use"));
            useBtn.setFocusPainted(false);
            String path = foundBinary.path();
            String id = harness.id();
            useBtn.addActionListener(e -> {
                disableButtons();
                if (selectionCallback != null) selectionCallback.onUse(id, path);
            });
            btnPanel.add(useBtn);
        } else {
            JButton installBtn = new JButton(NbBundle.getMessage(
                    OnboardingBubble.class, "OnboardingBubble.Button.Install"));
            installBtn.setFocusPainted(false);
            installBtn.addActionListener(e ->
                    BrowserUtils.openOrCopyUrl(harness.docsUrl(), null, null));
            btnPanel.add(installBtn);
        }

        row.add(iconLabel, BorderLayout.WEST);
        row.add(textPanel, BorderLayout.CENTER);
        row.add(btnPanel, BorderLayout.EAST);
        return row;
    }

    private BinaryResolver.FoundBinary findFoundBinary(String harnessId) {
        for (BinaryResolver.FoundBinary fb : foundBinaries) {
            if (fb.harnessId().equals(harnessId)) {
                return fb;
            }
        }
        return null;
    }

    /** Disables all action buttons so the user cannot trigger actions twice. */
    private void disableButtons() {
        if (buttonsPanel != null) {
            for (Component c : buttonsPanel.getComponents()) {
                c.setEnabled(false);
            }
        }
        setButtonsEnabledRecursive(rowsPanel, false);
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

    /** Non-wrapping small JTextArea used for body text. */
    private static final class JTextAreaNoWrap extends javax.swing.JTextArea {
        private static final long serialVersionUID = 1L;

        JTextAreaNoWrap(String text) {
            super(text);
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setOpaque(false);
            setFocusable(false);
            setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            setFont(ThemeManager.getFont().deriveFont(Font.PLAIN,
                    ThemeManager.getFont().getSize() + 1f));
            setForeground(ThemeManager.getCurrentTheme().foreground());
        }
    }
}
