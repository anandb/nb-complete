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
    /** Optional: dismisses the harness-chooser back to the chat. Non-null only
     *  when a harness is already configured (help-menu launch) — the
     *  first-install view must be non-dismissable. */
    private final Runnable dismissCallback;
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
            SelectionCallback selectionCallback) {
        this(found, selectionCallback, null);
    }

    /**
     * @param found harness binaries detected on this system (may be empty)
     * @param selectionCallback invoked when the user picks a found harness
     * @param dismissCallback invoked by the Close button; {@code null} hides
     *        the Close button (first-install view must be non-dismissable)
     */
    OnboardingBubble(java.util.List<BinaryResolver.FoundBinary> found,
            SelectionCallback selectionCallback,
            Runnable dismissCallback) {
        this.selectionCallback = selectionCallback;
        this.dismissCallback = dismissCallback;
        this.foundBinaries = found;

        setLayout(new BorderLayout());
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        ColorTheme theme = ThemeManager.getCurrentTheme();
        JPanel content = UIUtils.createBubbleContentPanel();

        // Header: punchy title + single-line subtitle
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        JLabel titleLabel = new JLabel(
                NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Title"));
        titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD,
                ThemeManager.getFont().getSize() + 4f));
        titleLabel.setForeground(theme.foreground());
        titleLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextAreaNoWrap subtitle = new JTextAreaNoWrap(NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.Subtitle"));
        subtitle.setForeground(theme.mutedForeground());
        subtitle.setBorder(BorderFactory.createEmptyBorder(2, 0, 10, 0));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(titleLabel);
        headerPanel.add(subtitle);
        content.add(headerPanel, BorderLayout.NORTH);

        // Partition catalog harnesses into installed vs. uninstalled
        java.util.List<HarnessCatalog.Harness> installed = new java.util.ArrayList<>();
        java.util.List<HarnessCatalog.Harness> uninstalled = new java.util.ArrayList<>();
        for (HarnessCatalog.Harness h : HarnessCatalog.ALL) {
            if (findFoundBinary(h.id()) != null) {
                installed.add(h);
            } else {
                uninstalled.add(h);
            }
        }
        String activePath = BinaryResolver.findExecutablePathOrNull();
        String activeHarnessId = activePath != null ? activeHarnessIdFor(activePath) : null;

        // Grouped sections: active card, installed rows, uninstalled rows, note
        rowsPanel = new JPanel(new GridBagLayout());
        rowsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 12, 0);
        if (activePath != null) {
            rowsPanel.add(createActiveCard(activeHarnessId, activePath, theme), gbc);
        }
        java.util.List<HarnessCatalog.Harness> switchable = new java.util.ArrayList<>();
        for (HarnessCatalog.Harness h : installed) {
            if (!h.id().equals(activeHarnessId)) {
                switchable.add(h);
            }
        }
        rowsPanel.add(createSectionLabel(NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.Section.Installed", switchable.size()), theme), gbc);
        for (HarnessCatalog.Harness harness : switchable) {
            rowsPanel.add(createInstalledRow(harness, false, theme), gbc);
        }
        rowsPanel.add(createSectionLabel(NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.Section.Uninstalled", uninstalled.size()), theme), gbc);
        for (HarnessCatalog.Harness harness : uninstalled) {
            rowsPanel.add(createUninstalledRow(harness, theme), gbc);
        }
        gbc.insets = new Insets(0, 0, 0, 0);
        rowsPanel.add(createPostActivationNote(theme), gbc);
        content.add(rowsPanel, BorderLayout.CENTER);

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
        buttonsPanel.add(manualSetupBtn);
        if (dismissCallback != null) {
            JButton closeBtn = new JButton(NbBundle.getMessage(
                    OnboardingBubble.class, "OnboardingBubble.Button.Close"));
            closeBtn.setFocusPainted(false);
            closeBtn.addActionListener(e -> dismissCallback.run());
            buttonsPanel.add(closeBtn);
        }

        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setOpaque(false);
        outer.add(content, BorderLayout.NORTH);
        outer.add(buttonsPanel, BorderLayout.SOUTH);
        add(outer, BorderLayout.CENTER);
    }

    /** Resolves the catalog id of the configured executable path via its binary basename. */
    private static String activeHarnessIdFor(String configuredPath) {
        String base = configuredPath == null ? "" : new java.io.File(configuredPath).getName().toLowerCase(Locale.ROOT);
        if (base.endsWith(".exe")) {
            base = base.substring(0, base.length() - 4);
        }
        HarnessCatalog.Harness h = HarnessCatalog.byBinaryName(base);
        return h == HarnessCatalog.UNKNOWN ? null : h.id();
    }

    /** Section header label, e.g. "INSTALLED HARNESSES (6 Available)". */
    private static JLabel createSectionLabel(String text, ColorTheme theme) {
        JLabel label = new JLabel(text);
        label.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        label.setForeground(theme.mutedForeground());
        label.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        return label;
    }

    /** Prominent card for the currently configured harness with a disabled Active badge. */
    private JPanel createActiveCard(String harnessId, String path, ColorTheme theme) {
        HarnessCatalog.Harness harness = harnessId != null ? HarnessCatalog.byId(harnessId) : HarnessCatalog.UNKNOWN;
        JPanel card = new JPanel(new BorderLayout(8, 4));
        card.setOpaque(true);
        card.setBackground(theme.isDark() ? new Color(0x2A2B33) : new Color(0xEDEDE8));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.bubbleBorder()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        card.add(createSectionLabel(NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.Section.Active"), theme), BorderLayout.NORTH);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(harnessIconLabel(harness), BorderLayout.WEST);
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        JLabel nameLabel = new JLabel(harness.displayName());
        nameLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(theme.foreground());
        JLabel pathLabel = new JLabel(path);
        pathLabel.setFont(IconResourceManager.getMonospaceFont().deriveFont(11f));
        pathLabel.setForeground(theme.mutedForeground());
        textPanel.add(nameLabel);
        textPanel.add(pathLabel);
        row.add(textPanel, BorderLayout.CENTER);
        JButton activeBtn = new JButton("\u2713 "
                + NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Badge.Active"));
        activeBtn.setEnabled(false);
        activeBtn.setFocusPainted(false);
        row.add(activeBtn, BorderLayout.EAST);
        card.add(row, BorderLayout.CENTER);
        JLabel auth = new JLabel("\u24D8 " + NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.AuthWarning"));
        auth.setFont(ThemeManager.getFont().deriveFont(11f));
        auth.setForeground(theme.mutedForeground());
        auth.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        card.add(auth, BorderLayout.SOUTH);
        return card;
    }

    /** Installed row: disclosure triangle hides the path; Use button, or Active badge when current. */
    private JPanel createInstalledRow(HarnessCatalog.Harness harness, boolean isActive, ColorTheme theme) {
        BinaryResolver.FoundBinary foundBinary = findFoundBinary(harness.id());
        String path = foundBinary != null ? foundBinary.path() : "";
        JPanel cell = new JPanel(new BorderLayout(0, 2));
        cell.setOpaque(false);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel pathLabel = new JLabel(path);
        pathLabel.setFont(IconResourceManager.getMonospaceFont().deriveFont(11f));
        pathLabel.setForeground(theme.mutedForeground());
        pathLabel.setVisible(false);
        JButton disclosure = new JButton(">");
        disclosure.setFocusPainted(false);
        disclosure.setMargin(new Insets(0, 2, 0, 2));
        disclosure.setToolTipText(NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Hint.Expand"));
        disclosure.addActionListener(e -> {
            boolean showing = !pathLabel.isVisible();
            pathLabel.setVisible(showing);
            disclosure.setText(showing ? "v" : ">");
            disclosure.setToolTipText(NbBundle.getMessage(OnboardingBubble.class,
                    showing ? "OnboardingBubble.Hint.Collapse" : "OnboardingBubble.Hint.Expand"));
            revalidate();
            repaint();
        });
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(disclosure);
        left.add(harnessIconLabel(harness));
        row.add(left, BorderLayout.WEST);
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        JLabel nameLabel = new JLabel(harness.displayName());
        nameLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(theme.foreground());
        textPanel.add(nameLabel);
        textPanel.add(pathLabel);
        row.add(textPanel, BorderLayout.CENTER);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setOpaque(false);
        if (isActive) {
            JButton activeBtn = new JButton("\u2713 "
                    + NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Badge.Active"));
            activeBtn.setEnabled(false);
            activeBtn.setFocusPainted(false);
            btnPanel.add(activeBtn);
        } else {
            JButton useBtn = new JButton(NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Button.Use"));
            useBtn.setFocusPainted(false);
            useBtn.setIcon(ThemeManager.getIcon("check.svg", 14));
            useBtn.setIconTextGap(6);
            String id = harness.id();
            useBtn.addActionListener(e -> {
                disableButtons();
                if (selectionCallback != null) {
                    selectionCallback.onUse(id, path);
                }
            });
            btnPanel.add(useBtn);
        }
        row.add(btnPanel, BorderLayout.EAST);
        cell.add(row, BorderLayout.NORTH);
        return cell;
    }

    /** Uninstalled row: Get Plugin button toggles the copy-command install panel with progress state. */
    private JPanel createUninstalledRow(HarnessCatalog.Harness harness, ColorTheme theme) {
        JPanel cell = new JPanel(new BorderLayout(0, 2));
        cell.setOpaque(false);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(harnessIconLabel(harness), BorderLayout.WEST);
        JLabel nameLabel = new JLabel(harness.displayName());
        nameLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(theme.foreground());
        row.add(nameLabel, BorderLayout.CENTER);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setOpaque(false);
        JLabel progress = new JLabel("\u273B "
                + NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Button.Installing"));
        progress.setForeground(theme.mutedForeground());
        progress.setVisible(false);
        JButton installBtn = new JButton(NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.Button.GetPlugin"));
        installBtn.setFocusPainted(false);
        installBtn.setIcon(ThemeManager.getIcon("download.svg", 14));
        installBtn.setIconTextGap(6);
        styleSecondaryButton(installBtn, theme);
        btnPanel.add(progress);
        btnPanel.add(installBtn);
        row.add(btnPanel, BorderLayout.EAST);
        cell.add(row, BorderLayout.NORTH);
        JPanel installPanel = createInstallPanel(harness, theme);
        installPanel.setVisible(false);
        installPanels.put(harness.id(), installPanel);
        cell.add(installPanel, BorderLayout.CENTER);
        installBtn.addActionListener(e -> {
            boolean showing = installPanel.isVisible();
            hideAllInstallPanels();
            installPanel.setVisible(!showing);
            progress.setVisible(!showing);
            revalidate();
            repaint();
        });
        return cell;
    }

    /** Collapsible post-activation note: auth in CLI/TUI + IDE restart. */
    private JPanel createPostActivationNote(ColorTheme theme) {
        JPanel note = new JPanel(new BorderLayout(4, 4));
        note.setOpaque(true);
        note.setBackground(theme.isDark() ? new Color(0x2A2B33) : new Color(0xF5F3EC));
        note.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.bubbleBorder()),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JButton header = new JButton("\u26A0 "
                + NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Note") + "  "
                + NbBundle.getMessage(OnboardingBubble.class, "OnboardingBubble.Note.Title"));
        header.setFocusPainted(false);
        header.setHorizontalAlignment(JButton.LEFT);
        header.setContentAreaFilled(false);
        header.setBorderPainted(false);
        JTextAreaNoWrap body = new JTextAreaNoWrap(NbBundle.getMessage(
                OnboardingBubble.class, "OnboardingBubble.Note.Body"));
        body.setForeground(theme.mutedForeground());
        body.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        body.setVisible(true);
        header.addActionListener(e -> {
            body.setVisible(!body.isVisible());
            revalidate();
            repaint();
        });
        note.add(header, BorderLayout.NORTH);
        note.add(body, BorderLayout.CENTER);
        return note;
    }

    /** Theme-aware harness icon label with generic fallback. */
    private static JLabel harnessIconLabel(HarnessCatalog.Harness harness) {
        Icon icon = ThemeManager.getIcon(harness.iconBase() + ".svg", 32);
        if (icon == null) {
            icon = ThemeManager.getIcon("agent.svg", 32);
        }
        JLabel label = new JLabel(icon);
        label.setVerticalAlignment(JLabel.TOP);
        return label;
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

        // Gemini-specific note, shown only once the user picks this harness to
        // install (the "Use" path shows the same message in a dialog).
        if ("gemini".equals(harness.id())) {
            JTextAreaNoWrap modelHint = new JTextAreaNoWrap(
                    NbBundle.getMessage(OnboardingBubble.class, "MSG_GeminiDefaultModel"), 10f);
            modelHint.setForeground(theme.mutedForeground());
            modelHint.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            modelHint.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(modelHint);
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

    /** Styles a button as secondary action: subtle border + hover tint, matching
     *  the Use button's icon+text treatment but without filled brand color. */
    private static void styleSecondaryButton(JButton btn, ColorTheme theme) {
        Color border = theme.isDark() ? new Color(0x5A5A5A) : new Color(0xCCCCCC);
        Color fg = theme.foreground();
        Color hoverBg = theme.isDark() ? new Color(0x2A2A2A) : new Color(0xF5F5F5);
        Color hoverBorder = theme.isDark() ? new Color(0x7A7A7A) : new Color(0x999999);
        btn.setForeground(fg);
        btn.setOpaque(true);
        btn.setBackground(theme.isDark() ? new Color(0x1E1E1E) : Color.WHITE);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(hoverBg);
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(hoverBorder, 1),
                        BorderFactory.createEmptyBorder(6, 14, 6, 14)));
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(theme.isDark() ? new Color(0x1E1E1E) : Color.WHITE);
                btn.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(border, 1),
                        BorderFactory.createEmptyBorder(6, 14, 6, 14)));
            }
        });
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
