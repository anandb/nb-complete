package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.support.AgentUtils;
import github.anandb.netbeans.support.PreferenceKeys;

final class ChatLayoutBuilder {

    private final AssistantTopComponent topComponent;
    private final ChatThreadPanel chatPanel;
    private final ConfigPanelController configPanelController;

    private UIUtils.WrappingComboBox<?> sessionDropdown;
    private JButton hideBtn;
    private JButton showHiddenBtn;
    private JButton newSessionBtn;
    private JButton renameSessionBtn;
    private JButton toggleBlocksBtn;
    private JButton keepBtn;
    private JButton filterBtn;
    private JButton helpBtn;
    private JButton toggleOptionsBtn;
    private JLabel statusLabel;
    private JLabel versionLabel;
    private JLabel cwdLabel;
    private PlaceholderTextArea inputArea;
    private JScrollPane inputScrollPane;
    private JPanel header;
    private JButton sendBtn;
    private JButton stopBtn;
    private JButton restartServerBtn;
    private JButton refreshBtn;
    private JButton exportBtn;
    private JPanel rightStatusPanel;
    private final javax.swing.Timer newSessionDebounceTimer;

    ChatLayoutBuilder(AssistantTopComponent topComponent, ChatThreadPanel chatPanel,
            ConfigPanelController configPanelController) {
        this.topComponent = topComponent;
        this.chatPanel = chatPanel;
        this.configPanelController = configPanelController;
        this.newSessionDebounceTimer = new javax.swing.Timer(2000, e -> fireNewSession());
        this.newSessionDebounceTimer.setRepeats(false);
    }

    JPanel buildHeader() {
        header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 12, 8, 12));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);

        sessionDropdown = new UIUtils.WrappingComboBox<>();
        sessionDropdown.getAccessibleContext().setAccessibleName("Session selector");
        sessionDropdown.getAccessibleContext().setAccessibleDescription("Select a chat session");
        sessionDropdown.addActionListener(e -> {
            if (!sessionDropdown.isPopupVisible()) {
                Object sel = sessionDropdown.getSelectedItem();
                if (sel instanceof github.anandb.netbeans.model.SessionItem item) {
                    sessionDropdown.setToolTipText(item.getTitle());
                }
            }
        });

        // Right-click context menu on session dropdown — install via
        // componentPopupMenu so Swing's JComponent handles popup trigger
        sessionDropdown.setComponentPopupMenu(new JPopupMenu() {
            @Override
            public void show(java.awt.Component invoker, int x, int y) {
                removeAll();
                Object sel = sessionDropdown.getSelectedItem();
                if (sel instanceof github.anandb.netbeans.model.SessionItem item) {
                    String sessionId = item.getSession().id();
                    boolean hidden = Lookup.getDefault()
                        .lookup(github.anandb.netbeans.contract.SessionControl.class)
                        .isHidden(sessionId);

                    JMenuItem rename = new JMenuItem("Rename");
                    rename.addActionListener(ev -> topComponent.renameCurrentSession());
                    add(rename);

                    JMenuItem archive = new JMenuItem(hidden ? "Unarchive" : "Archive");
                    archive.addActionListener(ev -> {
                        github.anandb.netbeans.contract.SessionControl sc =
                            Lookup.getDefault().lookup(
                                github.anandb.netbeans.contract.SessionControl.class);
                        sc.setHidden(sessionId, !hidden);
                        sc.refreshSessions();
                    });
                    add(archive);

                    addSeparator();

                    JMenuItem reload = new JMenuItem("Reload");
                    reload.addActionListener(ev -> topComponent.reloadCurrentSession());
                    add(reload);
                }
                super.show(invoker, x, y);
            }
        });

        JPanel sessionControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sessionControls.setOpaque(false);

        // Archive/Unarchive toggle for current session (debounced to prevent accidental double-click)
        final JButton[] hbRef = new JButton[1];
        JButton hb = UIUtils.createToolbarButton("archive.svg", NbBundle.getMessage(AssistantTopComponent.class, "HINT_ArchiveSession"), e -> {
            JButton btn = hbRef[0];
            btn.setEnabled(false);
            javax.swing.Timer timer = new javax.swing.Timer(500, ev -> {
                btn.setEnabled(true);
            });
            timer.setRepeats(false);
            timer.start();
            String sid = Lookup.getDefault().lookup(github.anandb.netbeans.contract.SessionControl.class).getCurrentSessionId();
            if (sid != null) {
                boolean currentlyHidden = Lookup.getDefault().lookup(github.anandb.netbeans.contract.SessionControl.class).isHidden(sid);
                Lookup.getDefault().lookup(github.anandb.netbeans.contract.SessionControl.class).setHidden(sid, !currentlyHidden);
                Lookup.getDefault().lookup(github.anandb.netbeans.contract.SessionControl.class).refreshSessions();
            } else {
                btn.setEnabled(true);
            }
        });
        hbRef[0] = hb;
        hideBtn = hb;

        // Show/hidden sessions filter toggle
        final JButton[] shbRef = new JButton[1];
        JButton shb = UIUtils.createToolbarButton("show.svg", NbBundle.getMessage(AssistantTopComponent.class, "HINT_ShowArchivedSessions"), e -> {
            boolean showing = !isShowingHidden();
            NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean("showHiddenSessions", showing);
            shbRef[0].setIcon(ThemeManager.getIcon(showing ? "hide.svg" : "show.svg", 28));
            shbRef[0].setToolTipText(showing
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_HideArchivedSessions")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ShowArchivedSessions"));
            Lookup.getDefault().lookup(github.anandb.netbeans.contract.SessionControl.class).refreshSessionList();
        });
        shbRef[0] = shb;
        showHiddenBtn = shb;
        // Restore icon state from preference
        if (isShowingHidden()) {
            shb.setIcon(ThemeManager.getIcon("hide.svg", 28));
            shb.setToolTipText(NbBundle.getMessage(AssistantTopComponent.class, "HINT_HideArchivedSessions"));
        }

        newSessionBtn = UIUtils.createToolbarButton("new.svg", NbBundle.getMessage(AssistantTopComponent.class, "HINT_NewSession"), e -> {
            newSessionDebounceTimer.restart();
        });
        String renameHint = NbBundle.getMessage(AssistantTopComponent.class, "HINT_RenameSession");
        renameSessionBtn = UIUtils.createToolbarButton("rename.svg", renameHint, e -> topComponent.renameCurrentSession());
        refreshBtn = UIUtils.createToolbarButton("reload.svg",
                NbBundle.getMessage(AssistantTopComponent.class, "HINT_ReloadConversation"), e -> {
            topComponent.reloadCurrentSession();
        });

        String exportHint = NbBundle.getMessage(AssistantTopComponent.class, "HINT_ExportConversation");
        exportBtn = UIUtils.createToolbarButton("export.svg", exportHint, e -> {
            topComponent.exportConversation();
        });
        String restartHint = NbBundle.getMessage(AssistantTopComponent.class, "HINT_RestartServer");
        restartServerBtn = UIUtils.createToolbarButton("restart.svg", restartHint, e -> {
            topComponent.promptRestartServer();
        });

        JButton tb = UIUtils.createToolbarButton("expand.svg", NbBundle.getMessage(AssistantTopComponent.class, "HINT_ExpandAll"), null);
        tb.addActionListener(e -> {
            boolean expanded = !chatPanel.isAllBlocksExpanded();
            chatPanel.toggleAllBlocks(expanded);
            String newState = expanded ? "collapse" : "expand";
            tb.putClientProperty("state", newState);
            tb.setToolTipText(expanded
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_CollapseAll")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ExpandAll"));
            tb.setIcon(ThemeManager.getIcon(expanded ? "collapse.svg" : "expand.svg", 28));
        });
        toggleBlocksBtn = tb;
        toggleBlocksBtn.putClientProperty("state", "expand");

        final boolean savedKeepState = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).getBoolean("keepOlderMessages", false);
        chatPanel.setKeepOlderMessages(savedKeepState);
        JButton pinBtn = UIUtils.createToolbarButton(savedKeepState ? "pin_off.svg" : "pin.svg",
                NbBundle.getMessage(AssistantTopComponent.class, savedKeepState ? "HINT_TruncateMessages" : "HINT_KeepMessages"), null);
        pinBtn.addActionListener(e -> {
            boolean keep = !chatPanel.isKeepOlderMessages();
            chatPanel.setKeepOlderMessages(keep);
            NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean("keepOlderMessages", keep);
            pinBtn.setIcon(ThemeManager.getIcon(keep ? "pin_off.svg" : "pin.svg", 28));
            pinBtn.setToolTipText(keep
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_TruncateMessages")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_KeepMessages"));
        });
        keepBtn = pinBtn;
        keepBtn.putClientProperty("state", savedKeepState ? "pinned" : "unpinned");

        filterBtn = createFilterButton();

        sessionControls.add(hideBtn);
        sessionControls.add(newSessionBtn);
        sessionControls.add(renameSessionBtn);
        sessionControls.add(refreshBtn);
        sessionControls.add(keepBtn);
        sessionControls.add(toggleBlocksBtn);
        sessionControls.add(filterBtn);
        sessionControls.add(exportBtn);
        sessionControls.add(restartServerBtn);

        // Apply saved toolbar visibility
        applyToolbarVisibility();

        // Right-click context menu for toolbar customization — install on panel and all buttons
        sessionControls.setComponentPopupMenu(newToolBarPopup());
        for (java.awt.Component c : sessionControls.getComponents()) {
            if (c instanceof javax.swing.JComponent jc) {
                jc.setComponentPopupMenu(newToolBarPopup());
            }
        }

        JPanel dropdownWrapper = new JPanel(new BorderLayout(4, 0));
        dropdownWrapper.setOpaque(false);
        dropdownWrapper.add(showHiddenBtn, BorderLayout.WEST);
        dropdownWrapper.add(sessionDropdown, BorderLayout.CENTER);

        topBar.add(dropdownWrapper, BorderLayout.CENTER);
        topBar.add(sessionControls, BorderLayout.EAST);

        cwdLabel = new JLabel("");
        cwdLabel.setFont(cwdLabel.getFont().deriveFont(Font.BOLD));

        cwdLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                topComponent.showCwdContextMenu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                topComponent.showCwdContextMenu(e);
            }
        });

        JPanel headerContent = new JPanel(new BorderLayout(0, 4));
        headerContent.setOpaque(false);

        JPanel cwdRow = new JPanel(new BorderLayout(4, 0));
        cwdRow.setOpaque(false);
        cwdRow.add(cwdLabel, BorderLayout.CENTER);

        String quickstartUrl = "https://github.com/anandb/nb-complete/blob/main/QUICKSTART.md";
        String feedbackUrl = "https://forms.gle/ZQn5Wy2aDSSpkzkaA";

        helpBtn = UIUtils.createToolbarButton("help.svg",
            NbBundle.getMessage(AssistantTopComponent.class, "HINT_QuickstartGuide"), null);
        helpBtn.setContentAreaFilled(false);
        helpBtn.setBorderPainted(false);
        helpBtn.addActionListener(e -> github.anandb.netbeans.support.BrowserUtils.openOrCopyUrl(quickstartUrl, "STATUS_QuickstartCopied",
            (url, key) -> topComponent.statusController.setStatus(key, url)));

        JButton feedbackBtn = UIUtils.createToolbarButton("feedback.svg",
            NbBundle.getMessage(AssistantTopComponent.class, "HINT_Feedback"), null);
        feedbackBtn.setContentAreaFilled(false);
        feedbackBtn.setBorderPainted(false);
        feedbackBtn.addActionListener(e -> github.anandb.netbeans.support.BrowserUtils.openOrCopyUrl(feedbackUrl, "STATUS_FeedbackCopied",
            (url, key) -> topComponent.statusController.setStatus(key, url)));

        JButton keyboardShortcutsBtn = UIUtils.createToolbarButton("keyboard.svg",
            NbBundle.getMessage(AssistantTopComponent.class, "HINT_KeyboardShortcuts"), null);
        keyboardShortcutsBtn.setContentAreaFilled(false);
        keyboardShortcutsBtn.setBorderPainted(false);
        if (keyboardShortcutsBtn.getIcon() == null) {
            keyboardShortcutsBtn.setText("\u2328");
            keyboardShortcutsBtn.setFont(keyboardShortcutsBtn.getFont().deriveFont(16f));
        }
        keyboardShortcutsBtn.addActionListener(e -> KeyboardShortcutsDialog.show(topComponent));

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightButtons.setOpaque(false);
        rightButtons.add(keyboardShortcutsBtn);
        rightButtons.add(feedbackBtn);
        rightButtons.add(helpBtn);
        cwdRow.add(rightButtons, BorderLayout.EAST);

        HelpButtonFlash.flash(helpBtn);

        headerContent.add(cwdRow, BorderLayout.NORTH);
        headerContent.add(topBar, BorderLayout.SOUTH);

        header.add(headerContent, BorderLayout.CENTER);

        return header;
    }

    JPanel buildBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(new EmptyBorder(4, 12, 4, 12));
        statusPanel.setOpaque(false);

        statusLabel = new JLabel(NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Ready"));
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        toggleOptionsBtn = UIUtils.createToolbarButton("settings.svg", 25, NbBundle.getMessage(AssistantTopComponent.class, "HINT_Options"), null);

        rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rightStatusPanel.setOpaque(false);

        rightStatusPanel.add(toggleOptionsBtn);

        statusPanel.add(rightStatusPanel, BorderLayout.EAST);

        bottomPanel.add(statusPanel, BorderLayout.NORTH);

        inputArea = new PlaceholderTextArea(NbBundle.getMessage(AssistantTopComponent.class, "LBL_TypeMessage"));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        inputArea.setRows(2);
        int editorFontSize = ThemeManager.getMonospaceFont().getSize();
        inputArea.setFont(ThemeManager.getFont().deriveFont(editorFontSize));

        inputScrollPane = new JScrollPane(inputArea);

        JPanel inputMainPanel = new JPanel(new BorderLayout(0, 4));
        inputMainPanel.setOpaque(false);
        inputMainPanel.add(configPanelController.getComponent(), BorderLayout.NORTH);
        inputMainPanel.add(inputScrollPane, BorderLayout.CENTER);

        JPanel btnCard = UIUtils.createTransparentPanel(new CardLayout());
        sendBtn = UIUtils.createTextButton(NbBundle.getMessage(AssistantTopComponent.class, "BTN_Go"), null);
        sendBtn.setMnemonic(java.awt.event.KeyEvent.VK_G);
        sendBtn.setDisplayedMnemonicIndex(0);
        stopBtn = UIUtils.createTextButton(NbBundle.getMessage(AssistantTopComponent.class, "BTN_Stop"), null);
        stopBtn.setMnemonic(java.awt.event.KeyEvent.VK_S);
        stopBtn.setDisplayedMnemonicIndex(0);

        btnCard.add(sendBtn, "SEND");
        btnCard.add(stopBtn, "STOP");

        String version = AgentUtils.getVersion();
        String releasesUrl = "https://github.com/anandb/nb-complete/releases/tag/v" + version;
        versionLabel = new JLabel("<html><u>v" + version + "</u></html>");
        Font labelFont = UIManager.getFont("Label.font");
        versionLabel.setFont(versionLabel.getFont().deriveFont(labelFont != null ? labelFont.getSize() - 1f : 9f));
        versionLabel.setForeground(ThemeManager.getCurrentTheme().mutedForeground());
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        versionLabel.setToolTipText("View Release Notes");
        versionLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        versionLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                github.anandb.netbeans.support.BrowserUtils.openOrCopyUrl(releasesUrl, null, null);
            }
        });

        JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
        rightPanel.setOpaque(false);
        rightPanel.add(btnCard, BorderLayout.CENTER);
        rightPanel.add(versionLabel, BorderLayout.SOUTH);

        inputMainPanel.add(rightPanel, BorderLayout.EAST);

        bottomPanel.add(inputMainPanel, BorderLayout.CENTER);

        return bottomPanel;
    }

    private JButton createFilterButton() {
        final JButton[] btnRef = new JButton[1];
        JButton btn = UIUtils.createToolbarButton("filter.svg", 25, NbBundle.getMessage(AssistantTopComponent.class, "HINT_FilterMessages"), e -> {
            javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
            for (String type : MessageFilterManager.getEffectiveMessageTypes()) {
                javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(type, !MessageFilterManager.isTypeHidden(type));
                item.addActionListener(ev -> {
                    MessageFilterManager.setTypeHidden(type, !item.isSelected());
                    chatPanel.applyTypeFilters();
                });
                popup.add(item);
            }
            popup.show(btnRef[0], 0, btnRef[0].getHeight());
        });
        btnRef[0] = btn;
        return btn;
    }

    private static boolean isDefaultVisible(String key) {
        return !PreferenceKeys.TOOLBAR_ARCHIVE.equals(key)
                && !PreferenceKeys.TOOLBAR_EXPAND_COLLAPSE.equals(key);
    }

    private JPopupMenu newToolBarPopup() {
        JPopupMenu popup = new JPopupMenu();
        JCheckBoxMenuItem editItem = new JCheckBoxMenuItem("Edit ToolBar", true);
        editItem.setEnabled(false);
        popup.add(editItem);
        popup.addSeparator();

        Object[][] buttons = {
            {hideBtn, PreferenceKeys.TOOLBAR_ARCHIVE, "Archive"},
            {newSessionBtn, PreferenceKeys.TOOLBAR_NEW_SESSION, "New Session"},
            {renameSessionBtn, PreferenceKeys.TOOLBAR_RENAME_SESSION, "Rename Session"},
            {refreshBtn, PreferenceKeys.TOOLBAR_RELOAD, "Reload"},
            {keepBtn, PreferenceKeys.TOOLBAR_KEEP, "Keep Messages"},
            {toggleBlocksBtn, PreferenceKeys.TOOLBAR_EXPAND_COLLAPSE, "Expand/Collapse All"},
            {filterBtn, PreferenceKeys.TOOLBAR_FILTER, "Filter"},
            {exportBtn, PreferenceKeys.TOOLBAR_EXPORT, "Export"},
            {restartServerBtn, PreferenceKeys.TOOLBAR_RESTART, "Restart Server"},
        };

        for (Object[] entry : buttons) {
            JButton btn = (JButton) entry[0];
            String key = (String) entry[1];
            String label = (String) entry[2];
            boolean visible = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                    .getBoolean(key, isDefaultVisible(key));
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(label, visible);
            item.addActionListener(e -> {
                NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                        .putBoolean(key, item.isSelected());
                btn.setVisible(item.isSelected());
            });
            popup.add(item);
        }

        return popup;
    }

    private void applyToolbarVisibility() {
        Object[][] btns = {
            {hideBtn, PreferenceKeys.TOOLBAR_ARCHIVE},
            {newSessionBtn, PreferenceKeys.TOOLBAR_NEW_SESSION},
            {renameSessionBtn, PreferenceKeys.TOOLBAR_RENAME_SESSION},
            {refreshBtn, PreferenceKeys.TOOLBAR_RELOAD},
            {keepBtn, PreferenceKeys.TOOLBAR_KEEP},
            {toggleBlocksBtn, PreferenceKeys.TOOLBAR_EXPAND_COLLAPSE},
            {filterBtn, PreferenceKeys.TOOLBAR_FILTER},
            {exportBtn, PreferenceKeys.TOOLBAR_EXPORT},
            {restartServerBtn, PreferenceKeys.TOOLBAR_RESTART},
        };
        for (Object[] pair : btns) {
            boolean visible = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                    .getBoolean((String) pair[1], isDefaultVisible((String) pair[1]));
            ((JButton) pair[0]).setVisible(visible);
        }
    }

    @SuppressWarnings("unchecked")
    JComboBox<github.anandb.netbeans.model.SessionItem> getSessionDropdown() {
        return (JComboBox<github.anandb.netbeans.model.SessionItem>) sessionDropdown;
    }

    JButton getHideBtn() { return hideBtn; }

    JButton getShowHiddenBtn() { return showHiddenBtn; }

    private void fireNewSession() {
        org.netbeans.api.project.Project[] projects = github.anandb.netbeans.project.ACPProjectManager.getInstance().getAllOpenProjects();
        if (projects == null || projects.length == 0) {
            return;
        }
        if (projects.length == 1) {
            Lookup.getDefault()
                .lookup(github.anandb.netbeans.contract.SessionControl.class)
                .createNewSession(projects[0].getProjectDirectory().getPath());
        } else {
            topComponent.showProjectPickerPopup(newSessionBtn);
        }
    }

    JButton getNewSessionBtn() { return newSessionBtn; }

    JButton getRenameSessionBtn() { return renameSessionBtn; }

    JButton getToggleBlocksBtn() { return toggleBlocksBtn; }

    JButton getKeepBtn() { return keepBtn; }

    JButton getFilterBtn() { return filterBtn; }

    JButton getHelpBtn() { return helpBtn; }

    JButton getToggleOptionsBtn() { return toggleOptionsBtn; }

    JLabel getStatusLabel() { return statusLabel; }

    JLabel getVersionLabel() { return versionLabel; }

    JLabel getCwdLabel() { return cwdLabel; }

    PlaceholderTextArea getInputArea() { return inputArea; }

    JScrollPane getInputScrollPane() { return inputScrollPane; }

    JPanel getHeader() { return header; }

    JButton getSendBtn() { return sendBtn; }

    JButton getStopBtn() { return stopBtn; }

    JPanel getRightStatusPanel() { return rightStatusPanel; }

    JButton getRestartServerBtn() { return restartServerBtn; }

    void updateHideButtonIcon(boolean hidden) {
        if (hideBtn != null) {
            hideBtn.setIcon(ThemeManager.getIcon(hidden ? "unarchive.svg" : "archive.svg", 28));
            hideBtn.setToolTipText(hidden
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_UnarchiveSession")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ArchiveSession"));
        }
    }

    static boolean isShowingHidden() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).getBoolean("showHiddenSessions", false);
    }
}
