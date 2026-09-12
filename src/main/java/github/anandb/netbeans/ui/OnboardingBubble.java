package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;

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

    /** Delay before the copied-tip popup starts fading out (milliseconds). */
    private static final int COPY_POPUP_MS = 1200;


    private final SelectionCallback selectionCallback;
    private final RestartCallback restartCallback;
    private final java.util.List<BinaryResolver.FoundBinary> foundBinaries;
    private final Map<String, JPanel> installPanels = new HashMap<>();
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

        // Body text — depends on whether any harness was detected
        String bodyKey = foundBinaries.isEmpty()
                ? "OnboardingBubble.Body.None" : "OnboardingBubble.Body.Mixed";
        String text = NbBundle.getMessage(OnboardingBubble.class, bodyKey);
        JTextAreaNoWrap body = new JTextAreaNoWrap(text);
        body.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));
        content.add(body, BorderLayout.CENTER);

        // One row per catalog harness — GridBagLayout keeps every cell full-width
        // (a Y-axis BoxLayout mis-sizes a row to its preferred width once its
        // install panel becomes visible and wide).
        rowsPanel = new JPanel(new GridBagLayout());
        rowsPanel.setOpaque(false);
        var last = HarnessCatalog.ALL.get(HarnessCatalog.ALL.size() - 1);
        for (HarnessCatalog.Harness harness : HarnessCatalog.ALL) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = GridBagConstraints.RELATIVE;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            if (harness != last) {
                gbc.insets = new Insets(0, 0, 18, 0);
            }
            rowsPanel.add(createHarnessRow(harness, theme), gbc);
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
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if ("gemini".equals(harness.id())) {
            JTextAreaNoWrap modelHint = new JTextAreaNoWrap(
                    NbBundle.getMessage(OnboardingBubble.class, "MSG_GeminiDefaultModel"), 10f);
            modelHint.setForeground(theme.mutedForeground());
            modelHint.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(modelHint);
        }

        // "Use" for detected harnesses; "Install" toggles the show-and-copy
        // install panel for the missing ones.
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setOpaque(false);
        JPanel installPanel = null;
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
            installPanel = createInstallPanel(harness, theme);
            final JPanel togglePanel = installPanel;
            installPanels.put(harness.id(), togglePanel);
            togglePanel.setVisible(false);
            installBtn.addActionListener(e -> {
                boolean showing = togglePanel.isVisible();
                hideAllInstallPanels();
                togglePanel.setVisible(!showing);
                revalidate();
                repaint();
            });
            btnPanel.add(installBtn);
        }

        row.add(iconLabel, BorderLayout.WEST);
        row.add(textPanel, BorderLayout.CENTER);
        row.add(btnPanel, BorderLayout.EAST);

        // Row + its (hidden) install panel stack vertically in a cell.
        // BorderLayout: the row always spans the full cell width and the panel
        // fills what remains; invisible panels are skipped by BorderLayout.
        JPanel cell = new JPanel(new BorderLayout(0, 2));
        cell.setOpaque(false);
        cell.add(row, BorderLayout.NORTH);
        if (installPanel != null) {
            cell.add(installPanel, BorderLayout.CENTER);
        }
        return cell;
    }

    /** Show-and-copy install panel: per-OS command, copy-on-click, prerequisites note,
     *  and a documentation link. Hidden by default; toggled by the Install button. */
    private JPanel createInstallPanel(HarnessCatalog.Harness harness, ColorTheme theme) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        // Indent to align with the harness name (icon width + row gap) and add
        // breathing room above the command.
        panel.setBorder(BorderFactory.createEmptyBorder(10, 40, 4, 0));

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String cmd = os.contains("win") ? harness.installWindows()
                : os.contains("mac") ? harness.installMac()
                : harness.installLinux();

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
        // Listener on BOTH the panel and the label: mouse events are delivered
        // to the deepest component under the cursor, so clicks on the command
        // text itself would never reach a panel-only listener.
        java.awt.event.MouseAdapter copyOnClick = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                copyCommand(cmdLabel, cmd);
            }
        };
        cmdPanel.addMouseListener(copyOnClick);
        cmdLabel.addMouseListener(copyOnClick);
        panel.add(cmdPanel);

        if (!harness.prerequisites().isBlank()) {
            JTextAreaNoWrap prereq = new JTextAreaNoWrap(harness.prerequisites(), 10f);
            prereq.setForeground(theme.mutedForeground());
            prereq.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            prereq.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(prereq);
        }

        JButton docsBtn = new JButton(NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.Button.Docs"));
        docsBtn.setFocusPainted(false);
        docsBtn.addActionListener(e ->
                BrowserUtils.openOrCopyUrl(harness.docsUrl(), null, null));
        JPanel docsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        docsRow.setOpaque(false);
        docsRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
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

    /** Copies the command to the clipboard and shows a tooltip-style popup
     *  near the command that fades out after a moment. */
    private void copyCommand(JLabel label, String command) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(command), null);
        showCopiedTip(label);
    }

    /** Shows a brief "Copied to clipboard" tooltip-style popup next to the
     *  given anchor, fading out after a moment. */
    private void showCopiedTip(Component anchor) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        JLabel tip = new JLabel(NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Copied"));
        tip.setOpaque(true);
        tip.setBackground(theme.isDark() ? new Color(0x2A2B33) : new Color(0xFFFFE1));
        tip.setForeground(theme.foreground());
        tip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.bubbleBorder()),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        Point p = anchor.getLocationOnScreen();
        int x = p.x + 16;
        int y = p.y - tip.getPreferredSize().height - 4;
        Popup popup = PopupFactory.getSharedInstance().getPopup(anchor, tip, x, y);
        popup.show();
        resetOpacity(tip);
        Timer fadeTimer = new Timer(COPY_POPUP_MS, e -> fadeOutAndHide(popup, tip));
        fadeTimer.setRepeats(false);
        fadeTimer.start();
    }

    /** Restores full opacity on the tip's window (PopupFactory may reuse it;
     *  a previously faded window would make the next tip start out faded). */
    private static void resetOpacity(Component content) {
        Window window = SwingUtilities.getWindowAncestor(content);
        if (window != null) {
            try {
                window.setOpacity(1.0f);
            } catch (UnsupportedOperationException e) {
                // translucency unsupported — leave as-is
            }
        }
    }

    /** Fades the copied-tip window out (where translucency is supported) and hides it. */
    private static void fadeOutAndHide(Popup popup, Component content) {
        Window window = SwingUtilities.getWindowAncestor(content);
        if (window == null) {
            popup.hide();
            return;
        }
        try {
            boolean translucent = !GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
            if (translucent) {
                popup.hide();
                return;
            }
        } catch (HeadlessException e) {
            popup.hide();
            return;
        }
        javax.swing.Timer fade = new javax.swing.Timer(30, null);
        final float[] alpha = {1.0f};
        fade.addActionListener(e -> {
            alpha[0] -= 0.12f;
            if (alpha[0] <= 0.05f) {
                fade.stop();
                window.setOpacity(1.0f);
                popup.hide();
            } else {
                window.setOpacity(alpha[0]);
            }
        });
        fade.start();
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
        hideAllInstallPanels();
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
