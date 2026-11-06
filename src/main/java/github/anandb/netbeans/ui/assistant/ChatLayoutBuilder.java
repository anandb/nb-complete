package github.anandb.netbeans.ui.assistant;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.util.prefs.PreferenceChangeListener;

import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.support.AgentUtils;
import github.anandb.netbeans.support.AppLinks;
import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.PermissionAutoAllow;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.support.ProjectPaths;
import github.anandb.netbeans.support.UserGuideOpener;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.UpdateCheckerControl;
import github.anandb.netbeans.contract.UpdateCheckerControl.UpdateCheckResult;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.support.BrowserUtils;
import javax.swing.JComponent;
import javax.swing.Timer;
import org.netbeans.api.project.Project;
import github.anandb.netbeans.ui.chat.MessageFilterManager;
import github.anandb.netbeans.ui.chat.ChatThreadPanel;
import github.anandb.netbeans.ui.composer.PlaceholderTextArea;
import github.anandb.netbeans.ui.session.ConfigPanelController;
import github.anandb.netbeans.ui.permission.PermissionRequestPanel;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import github.anandb.netbeans.ui.rendering.RaisedIcon;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.options.KeyboardShortcutsDialog;
import github.anandb.netbeans.ui.stats.ContextUsageMeter;
import github.anandb.netbeans.ui.components.PulsingToggleButton;
import github.anandb.netbeans.ui.components.UIUtils;

// DSL-CONTROLLER: not a view — newSessionDebounceTimer (300ms) lives here per
// AGENTS.md. Construction body is the Phase 2 seam target (ChatLayoutSpec);
// the debounce timer stays imperative.
public final class ChatLayoutBuilder {

    private final AssistantTopComponent topComponent;
    private final ChatThreadPanel chatPanel;
    private final ConfigPanelController configPanelController;

    private UIUtils.WrappingComboBox<?> sessionDropdown;
    private JButton hideBtn;
    private JButton showHiddenBtn;
    private AttentionButton newSessionBtn;
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
    private JButton launchMiniAssistantBtn;
    private PreferenceChangeListener miniAssistantPrefListener;
    private JPanel rightStatusPanel;

    private ContextUsageMeter contextUsageMeter;
    private PermissionRequestPanel permissionPanel;
    private PermissionRequestPanel configConfirmPanel;
    private final Timer newSessionDebounceTimer;

    ChatLayoutBuilder(AssistantTopComponent topComponent, ChatThreadPanel chatPanel,
            ConfigPanelController configPanelController) {
        this.topComponent = topComponent;
        this.chatPanel = chatPanel;
        this.configPanelController = configPanelController;
        this.newSessionDebounceTimer = new Timer(300, e -> fireNewSession());
        this.newSessionDebounceTimer.setRepeats(false);
    }

    /**
     * Ink width fraction of each header asset at its own viewBox size, measured by
     * rasterizing the SVG and taking the alpha bounding box (240 px render):
     * lock 0.75, mini-assistant 0.717.
     * <p>
     * The line-art glyphs leave a lot of padding, so at the toolbar size their ink reads
     * narrower than the padlock's solid body. The padlock is drawn from these fractions so
     * its ink ends up as wide as the mini-assistant glyph at the default toolbar size.
     */
    private static final double LOCK_INK_WIDTH = 0.75;
    private static final double MINI_ASSISTANT_INK_WIDTH = 0.717;

    private static int lockIconSize() {
        int base = PluginSettings.getToolbarIconSize();
        return Math.max(1, Math.round((float) (base * MINI_ASSISTANT_INK_WIDTH / LOCK_INK_WIDTH)));
    }

    /**
     * The padlock's ink fills its square box, so it is geometrically centered yet reads low —
     * the body outweighs the shackle. Lift it by ~8% of its own size for optical centering
     * with the mini-assistant and help glyphs.
     */
    private static int lockRisePx() {
        return Math.max(1, Math.round(lockIconSize() * 0.08f));
    }

    /**
     * Replaces the factory's full-size padlock with the smaller, optically raised one.
     * The factory sized the button from the original asset, so the preferred size is
     * recalculated after the swap.
     */
    private static void applyLockIcon(PulsingToggleButton button) {
        Icon lock = ThemeManager.getIcon("lock.svg", lockIconSize());
        if (lock == null) {
            return;
        }
        int rise = lockRisePx();
        button.setIcon(new RaisedIcon(lock, rise));
        Icon disabled = ImageUtilities.createDisabledIcon(lock);
        if (disabled != null) {
            button.setDisabledIcon(new RaisedIcon(disabled, rise));
        }
        UIUtils.recalcToolbarButtonSize(button);
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
                if (sel instanceof SessionItem item) {
                    sessionDropdown.setToolTipText(item.getTitle());
                }
            }
        });

        // Right-click context menu on session dropdown — install via
        // componentPopupMenu so Swing's JComponent handles popup trigger
        sessionDropdown.setComponentPopupMenu(new JPopupMenu() {
            @Override
            public void show(Component invoker, int x, int y) {
                removeAll();
                Object sel = sessionDropdown.getSelectedItem();
                if (sel instanceof SessionItem item) {
                    String sessionId = item.getSession().id();
                    var tmpSc = Lookup.getDefault()
                        .lookup(SessionControl.class);

                    JMenuItem rename = new JMenuItem(NbBundle.getMessage(UIBundle.class, "LBL_Rename"));
                    rename.addActionListener(ev -> topComponent.renameCurrentSession());
                    add(rename);

                    JMenuItem fork = new JMenuItem(NbBundle.getMessage(UIBundle.class, "LBL_ForkSession"));
                    var forkSc = Lookup.getDefault().lookup(SessionControl.class);
                    fork.setEnabled(forkSc != null && forkSc.canForkSession());
                    fork.addActionListener(ev -> topComponent.forkSession(sessionId));
                    add(fork);

                    if (tmpSc != null) {
                        boolean hidden = tmpSc.isHidden(sessionId);
                        String archiveText = NbBundle.getMessage(UIBundle.class,
                                hidden ? "LBL_Unarchive" : "LBL_Archive");
                        JMenuItem archive = new JMenuItem(archiveText);
                        archive.addActionListener(ev -> {
                            var archSc = Lookup.getDefault().lookup(
                                SessionControl.class);
                            if (archSc == null) {
                                topComponent.setStatus("STATUS_ServiceUnavailable");
                                return;
                            }
                            boolean newHidden = !hidden;
                            archSc.setHidden(sessionId, newHidden);
                            archSc.refreshSessions();
                            if (newHidden && !isShowingHidden()) {
                                MiniAssistantDialog.closeIfVisible();
                            }
                        });
                        add(archive);

                        // Archive every session currently listed in the dropdown. Ids are
                        // captured up front because refreshSessions() rebuilds the items.
                        List<String> visibleIds = new ArrayList<>();
                        for (int i = 0; i < sessionDropdown.getItemCount(); i++) {
                            Object entry = sessionDropdown.getItemAt(i);
                            if (entry instanceof SessionItem it && it.getSession() != null) {
                                visibleIds.add(it.getSession().id());
                            }
                        }
                        List<String> otherIds = new ArrayList<>(visibleIds);
                        otherIds.remove(sessionId);

                        JMenuItem archiveAll = new JMenuItem(
                                NbBundle.getMessage(UIBundle.class, "LBL_ArchiveAll"));
                        archiveAll.setEnabled(!visibleIds.isEmpty());
                        archiveAll.addActionListener(ev -> archiveSessions(visibleIds, false));
                        add(archiveAll);

                        // Keeps the session the user is looking at; archives every other one.
                        JMenuItem archiveOthers = new JMenuItem(
                                NbBundle.getMessage(UIBundle.class, "LBL_ArchiveOthers"));
                        archiveOthers.setEnabled(!otherIds.isEmpty());
                        archiveOthers.addActionListener(ev -> archiveSessions(otherIds, true));
                        add(archiveOthers);

                        // ACP session/delete: the row is gone for good, unlike archiving.
                        JMenuItem delete = new JMenuItem(NbBundle.getMessage(UIBundle.class, "LBL_DeleteSession"));
                        delete.setEnabled(tmpSc.canDeleteSession());
                        delete.addActionListener(ev -> topComponent.deleteSession(sessionId));
                        add(delete);
                    }

                    addSeparator();

                    JMenuItem reload = new JMenuItem(NbBundle.getMessage(UIBundle.class, "LBL_Reload"));
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
        JButton hb = UIUtils.createToolbarButton("archive.svg", NbBundle.getMessage(UIBundle.class, "HINT_ArchiveSession"), e -> {
            JButton btn = hbRef[0];
            btn.setEnabled(false);
            Timer timer = new Timer(500, ev -> {
                btn.setEnabled(true);
            });
            timer.setRepeats(false);
            timer.start();
            var sc = Lookup.getDefault().lookup(SessionControl.class);
            if (sc == null) {
                btn.setEnabled(true);
                topComponent.setStatus("STATUS_ServiceUnavailable");
                return;
            }
            String sid = sc.getCurrentSessionId();
            if (sid != null) {
                boolean currentlyHidden = sc.isHidden(sid);
                boolean newHidden = !currentlyHidden;
                sc.setHidden(sid, newHidden);
                sc.refreshSessions();
                if (newHidden && !isShowingHidden()) {
                    MiniAssistantDialog.closeIfVisible();
                }
            } else {
                btn.setEnabled(true);
            }
        });
        hbRef[0] = hb;
        hideBtn = hb;

        // Show/hidden sessions filter toggle
        final JButton[] shbRef = new JButton[1];
        JButton shb = UIUtils.createToolbarButton("show.svg", NbBundle.getMessage(UIBundle.class, "HINT_ShowArchivedSessions"), e -> {
            boolean showing = !isShowingHidden();
            PluginSettings.setShowHiddenSessions(showing);
            shbRef[0].setIcon(ThemeManager.getIcon(showing ? "hide.svg" : "show.svg", PluginSettings.getToolbarIconSize()));
            shbRef[0].setToolTipText(showing
                ? NbBundle.getMessage(UIBundle.class, "HINT_HideArchivedSessions")
                : NbBundle.getMessage(UIBundle.class, "HINT_ShowArchivedSessions"));
            var sc = Lookup.getDefault().lookup(SessionControl.class);
            if (sc != null) {
                sc.refreshSessionList();
            } else {
                topComponent.setStatus("STATUS_ServiceUnavailable");
            }
        });
        shbRef[0] = shb;
        showHiddenBtn = shb;
        // Restore icon state from preference
        if (isShowingHidden()) {
            shb.setIcon(ThemeManager.getIcon("hide.svg", PluginSettings.getToolbarIconSize()));
            shb.setToolTipText(NbBundle.getMessage(UIBundle.class, "HINT_HideArchivedSessions"));
        }

        newSessionBtn = UIUtils.createAttentionToolbarButton(
                "new.svg",
                PluginSettings.getToolbarIconSize(),
                NbBundle.getMessage(UIBundle.class, "HINT_NewSession"),
                e -> newSessionDebounceTimer.restart()
        );
        String renameHint = NbBundle.getMessage(UIBundle.class, "HINT_RenameSession");
        renameSessionBtn = UIUtils.createToolbarButton("rename.svg", renameHint, e -> topComponent.renameCurrentSession());
        refreshBtn = UIUtils.createToolbarButton("reload.svg",
                NbBundle.getMessage(UIBundle.class, "HINT_ReloadConversation"), e -> {
            topComponent.reloadCurrentSession();
        });

        String exportHint = NbBundle.getMessage(UIBundle.class, "HINT_ExportConversation");
        exportBtn = UIUtils.createToolbarButton("export.svg", exportHint, e -> {
            JPopupMenu exportPopup = new JPopupMenu();
            JMenuItem mdItem = new JMenuItem(NbBundle.getMessage(
                    UIBundle.class, "LBL_ExportMarkDown"));
            mdItem.addActionListener(ev -> topComponent.exportConversationAs("md"));
            exportPopup.add(mdItem);
            JMenuItem htmlItem = new JMenuItem(NbBundle.getMessage(
                    UIBundle.class, "LBL_ExportHtml"));
            htmlItem.addActionListener(ev -> topComponent.exportConversationAs("html"));
            exportPopup.add(htmlItem);
            exportPopup.show(exportBtn, 0, exportBtn.getHeight());
        });
        String restartHint = NbBundle.getMessage(UIBundle.class, "HINT_RestartServer");
        restartServerBtn = UIUtils.createToolbarButton("restart.svg", restartHint, e -> {
            topComponent.promptRestartServer();
        });

        JButton tb = UIUtils.createToolbarButton("expand.svg", NbBundle.getMessage(UIBundle.class, "HINT_ExpandAll"), null);
        tb.addActionListener(e -> {
            boolean expanded = !chatPanel.isAllBlocksExpanded();
            chatPanel.toggleAllBlocks(expanded);
            String newState = expanded ? "collapse" : "expand";
            tb.putClientProperty("state", newState);
            tb.setToolTipText(expanded
                ? NbBundle.getMessage(UIBundle.class, "HINT_CollapseAll")
                : NbBundle.getMessage(UIBundle.class, "HINT_ExpandAll"));
            tb.setIcon(ThemeManager.getIcon(expanded ? "collapse.svg" : "expand.svg", PluginSettings.getToolbarIconSize()));
        });
        toggleBlocksBtn = tb;
        toggleBlocksBtn.putClientProperty("state", "expand");

        final boolean savedKeepState = PluginSettings.isKeepOlderMessages();
        chatPanel.setKeepOlderMessages(savedKeepState);
        JButton forgetBtn = UIUtils.createToolbarButton(savedKeepState ? "forget.svg" : "remember.svg",
                NbBundle.getMessage(UIBundle.class, savedKeepState ? "HINT_TruncateMessages" : "HINT_KeepMessages"), null);
        forgetBtn.addActionListener(e -> {
            boolean keep = !chatPanel.isKeepOlderMessages();
            chatPanel.setKeepOlderMessages(keep);
            PluginSettings.setKeepOlderMessages(keep);
            forgetBtn.setIcon(ThemeManager.getIcon(keep ? "forget.svg" : "remember.svg", PluginSettings.getToolbarIconSize()));
            forgetBtn.setToolTipText(keep
                ? NbBundle.getMessage(UIBundle.class, "HINT_TruncateMessages")
                : NbBundle.getMessage(UIBundle.class, "HINT_KeepMessages"));
        });
        keepBtn = forgetBtn;
        keepBtn.putClientProperty("state", savedKeepState ? "forget" : "remember");

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

        // Right-click context menu for toolbar customization — single shared
        // popup, rebuilt on each show so checkbox state syncs with prefs.
        JPopupMenu sharedPopup = newToolBarPopup();
        sessionControls.setComponentPopupMenu(sharedPopup);
        for (Component c : sessionControls.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setComponentPopupMenu(sharedPopup);
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

        cwdLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                topComponent.showCwdContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                topComponent.showCwdContextMenu(e);
            }
        });

        JPanel headerContent = new JPanel(new BorderLayout(0, 4));
        headerContent.setOpaque(false);

        JPanel cwdRow = new JPanel(new BorderLayout(4, 0));
        cwdRow.setOpaque(false);
        cwdRow.add(cwdLabel, BorderLayout.CENTER);

        String customGuideUrl = AppLinks.userGuideUrl();

        helpBtn = UIUtils.createToolbarButton("help.svg",
            NbBundle.getMessage(UIBundle.class, "HINT_QuickstartGuide"), null);
        helpBtn.setContentAreaFilled(false);
        helpBtn.setBorderPainted(false);
        helpBtn.addActionListener(e -> {
            NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                    .put(PreferenceKeys.HELP_FLASH_PENDING, "false");
            JPopupMenu helpPopup = new JPopupMenu();

            JMenuItem openGuideItem = new JMenuItem(NbBundle.getMessage(
                    UIBundle.class, "LBL_HelpOpenGuide"));
            openGuideItem.addActionListener(ev -> UserGuideOpener.open(guideFallback(customGuideUrl)));
            helpPopup.add(openGuideItem);

            JMenuItem checkUpdatesItem = new JMenuItem(NbBundle.getMessage(
                    UIBundle.class, "LBL_HelpCheckUpdates"));
            checkUpdatesItem.addActionListener(ev -> {
                UpdateCheckerControl updateChecker = Lookup.getDefault().lookup(UpdateCheckerControl.class);
                if (updateChecker == null) {
                    topComponent.setStatus("STATUS_UpdateCheckerUnavailable");
                } else {
                    topComponent.setStatus("STATUS_CheckingForUpdates");
                    updateChecker.checkNow().whenComplete((result, error) ->
                        SwingUtilities.invokeLater(() -> {
                            if (error != null || result == null
                                    || result == UpdateCheckResult.CHECK_FAILED) {
                                topComponent.setStatus("STATUS_UpdateCheckFailed");
                            } else if (result == UpdateCheckResult.UPDATE_AVAILABLE) {
                                topComponent.setStatus("STATUS_UpdateAvailable");
                            } else if (result == UpdateCheckResult.UPDATE_SKIPPED) {
                                topComponent.setStatus("STATUS_UpdateVersionSkipped");
                            } else {
                                topComponent.setStatus("STATUS_NoUpdatesAvailable");
                            }
                        }));
                }
            });
            helpPopup.add(checkUpdatesItem);

            JMenuItem shortcutsItem = new JMenuItem(NbBundle.getMessage(
                    UIBundle.class, "LBL_HelpKeyboardShortcuts"));
            shortcutsItem.addActionListener(ev -> KeyboardShortcutsDialog.show(topComponent));
            helpPopup.add(shortcutsItem);

            JMenuItem chooseAgentItem = new JMenuItem(NbBundle.getMessage(
                    UIBundle.class, "LBL_HelpChooseAgent"));
            chooseAgentItem.addActionListener(ev ->
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    java.util.List<BinaryResolver.FoundBinary> found = BinaryResolver.findAllKnownOnPath();
                    topComponent.showHarnessChooser(found);
                }));
            helpPopup.add(chooseAgentItem);

            helpPopup.addSeparator();
            JMenuItem supportItem = new JMenuItem(NbBundle.getMessage(
                    UIBundle.class, "LBL_HelpSupportProject"));
            // Deliberately plain: default weight, size and colour, LAF icon column size. A menu
            // row that shouts costs the tool credibility; the mark alone distinguishes it.
            supportItem.setIcon(ThemeManager.getIcon("bmc.svg", 16));
            supportItem.addActionListener(ev -> BrowserUtils.openOrCopyUrl(AppLinks.supportUrl(),
                    "STATUS_SupportUrlCopied", (url, key) -> topComponent.setStatus(key, url)));
            helpPopup.add(supportItem);

            JMenuItem reportIssueItem = new JMenuItem(NbBundle.getMessage(
                    UIBundle.class, "CTL_ReportIssue"));
            reportIssueItem.addActionListener(ev -> BrowserUtils.openOrCopyUrl(AppLinks.issuesUrl(),
                    "STATUS_IssueUrlCopied", (url, key) -> topComponent.setStatus(key, url)));
            helpPopup.add(reportIssueItem);

            helpPopup.show(helpBtn, 0, helpBtn.getHeight());
        });

        PulsingToggleButton breakModeBtn = UIUtils.createToggleToolbarButton("lock.svg", "take-break.svg",
                NbBundle.getMessage(UIBundle.class, "HINT_BreakModeOff"));
        applyLockIcon(breakModeBtn);
        breakModeBtn.setSelected(PermissionAutoAllow.isEnabled());
        updateBreakModeTooltip(breakModeBtn);
        breakModeBtn.addActionListener(e -> onBreakModeToggled(breakModeBtn));

        launchMiniAssistantBtn = UIUtils.createToolbarButton("mini-assistant.svg",
            "Launch Mini Assistant", null);
        launchMiniAssistantBtn.setContentAreaFilled(false);
        launchMiniAssistantBtn.setBorderPainted(false);
        launchMiniAssistantBtn.setVisible(PluginSettings.isMiniAssistantEnabled());
        launchMiniAssistantBtn.addActionListener(e -> {
            MiniAssistantDialog.getInstance().toggleVisibility();
        });

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightButtons.setOpaque(false);
        rightButtons.add(breakModeBtn);
        rightButtons.add(launchMiniAssistantBtn);
        rightButtons.add(helpBtn);
        cwdRow.add(rightButtons, BorderLayout.EAST);

        // Flash the help button until the user clicks on it
        String flashPending = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .get(PreferenceKeys.HELP_FLASH_PENDING, "false");
        if ("true".equals(flashPending)) {
            HelpButtonFlash.flash(helpBtn);
        }

        headerContent.add(cwdRow, BorderLayout.NORTH);
        headerContent.add(topBar, BorderLayout.SOUTH);

        permissionPanel = new PermissionRequestPanel();
        configConfirmPanel = new PermissionRequestPanel();

        header.add(headerContent, BorderLayout.CENTER);
        JPanel headerWrapper = new JPanel();
        headerWrapper.setLayout(new BoxLayout(headerWrapper, BoxLayout.Y_AXIS));
        headerWrapper.add(header);
        headerWrapper.add(permissionPanel);
        headerWrapper.add(configConfirmPanel);

        return headerWrapper;
    }

    JPanel buildBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());

                // GridBagLayout, not BorderLayout: the status text width varies constantly
        // ("Sending" -> "Sending..."), and a BorderLayout.WEST label would resize
        // on every animation tick, shifting the model dropdown (CENTER/EAST).
        // weightx=1 on the label makes it absorb all width changes; the dropdown
        // cluster on the right never moves.
        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.setBorder(new EmptyBorder(4, 12, 4, 12));
        statusPanel.setOpaque(false);

        statusLabel = new JLabel(NbBundle.getMessage(UIBundle.class, "STATUS_Ready"));
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        GridBagConstraints statusGbc = new GridBagConstraints();
        statusGbc.gridx = 0;
        statusGbc.gridy = 0;
        statusGbc.weightx = 0.0;
        statusGbc.fill = GridBagConstraints.NONE;
        statusGbc.anchor = GridBagConstraints.WEST;
        statusPanel.add(statusLabel, statusGbc);

        JPanel modelWrap = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() != null) {
                    int maxW = (int) (getParent().getWidth() * 0.70);
                    if (maxW > 0 && d.width > maxW) {
                        d = new Dimension(maxW, d.height);
                    }
                }
                return d;
            }
        };
        modelWrap.setOpaque(false);
        modelWrap.add(configPanelController.getModelCombo(), BorderLayout.CENTER);

        JPanel centerStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        centerStatusPanel.setOpaque(false);
        // Context usage meter: persistent state, always visible — including when
        // the options panel is collapsed (which hides only the model combo and
        // its copy button). Left of the model dropdown.
        contextUsageMeter = new ContextUsageMeter();
        centerStatusPanel.add(contextUsageMeter);
        centerStatusPanel.add(modelWrap);
        centerStatusPanel.add(configPanelController.getCopyModelBtn());
        statusGbc.gridx = 1;
        statusGbc.weightx = 1.0;
        statusGbc.fill = GridBagConstraints.HORIZONTAL;
        statusPanel.add(centerStatusPanel, statusGbc);

        int tsz = PluginSettings.getToolbarIconSize();
        toggleOptionsBtn = UIUtils.createToolbarButton("settings.svg", tsz,
                NbBundle.getMessage(UIBundle.class, "HINT_Options"), null);

        rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rightStatusPanel.setOpaque(false);

        rightStatusPanel.add(toggleOptionsBtn);

        statusGbc.gridx = 2;
        statusGbc.weightx = 0.0;
        statusGbc.fill = GridBagConstraints.VERTICAL;
        statusGbc.anchor = GridBagConstraints.EAST;
        statusPanel.add(rightStatusPanel, statusGbc);

        JPanel bottomNorth = new JPanel();
        bottomNorth.setLayout(new BoxLayout(bottomNorth, BoxLayout.Y_AXIS));
        bottomNorth.setOpaque(false);
        bottomNorth.add(statusPanel);
        bottomPanel.add(bottomNorth, BorderLayout.NORTH);

        inputArea = new PlaceholderTextArea(NbBundle.getMessage(UIBundle.class, "LBL_TypeMessage"));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        inputArea.setRows(3);
        int chatFontSize = ThemeManager.getFont().getSize() - 2;
        inputArea.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, chatFontSize + 2f));

        inputScrollPane = new JScrollPane(inputArea);
        // Prevent the input area from shrinking below its 2-row natural height,
        // otherwise a scrollbar appears when the split pane divider is dragged down.
        Dimension scrollMin = inputArea.getPreferredScrollableViewportSize();
        scrollMin.height += inputArea.getBorder().getBorderInsets(inputArea).top
                          + inputArea.getBorder().getBorderInsets(inputArea).bottom;
        inputScrollPane.setMinimumSize(scrollMin);

        JPanel inputMainPanel = new JPanel(new BorderLayout(0, 4));
        inputMainPanel.setOpaque(false);
        inputMainPanel.add(configPanelController.getComponent(), BorderLayout.NORTH);
        inputMainPanel.add(inputScrollPane, BorderLayout.CENTER);

        JPanel btnCard = UIUtils.createTransparentPanel(new CardLayout());
        String goText = NbBundle.getMessage(UIBundle.class, "BTN_Go");
        sendBtn = UIUtils.createTextButton(goText, null, true);
        applyHarnessIcon(sendBtn, goText);
        String agentName = BinaryResolver.resolveDisplayName();
        sendBtn.setToolTipText(goText + " (" + (agentName != null ? agentName : "Agent") + ")");
        sendBtn.setMnemonic(KeyEvent.VK_G);
        sendBtn.setDisplayedMnemonicIndex(0);
        stopBtn = UIUtils.createTextButton(NbBundle.getMessage(UIBundle.class, "BTN_Stop"), null);
        stopBtn.setPreferredSize(new Dimension(100, sendBtn.getPreferredSize().height));
        stopBtn.setMnemonic(KeyEvent.VK_S);
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
        versionLabel.setToolTipText(NbBundle.getMessage(UIBundle.class, "LBL_ViewReleaseNotes"));
        versionLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        versionLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                BrowserUtils.openOrCopyUrl(releasesUrl, null, null);
            }
        });

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);
        // Let the send/stop button card grow up to 2× its natural height
        // when the input area is enlarged via the split pane divider.
        int maxBtnH = btnCard.getPreferredSize().height * 2;
        btnCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxBtnH));
        btnCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(btnCard);
        rightPanel.add(Box.createVerticalGlue());
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        rightPanel.add(versionLabel);

        inputMainPanel.add(rightPanel, BorderLayout.EAST);

        bottomPanel.add(inputMainPanel, BorderLayout.CENTER);

        return bottomPanel;
    }

    private JButton createFilterButton() {
        int tsz = PluginSettings.getToolbarIconSize();
        final JButton[] btnRef = new JButton[1];
        JButton btn = UIUtils.createToolbarButton("filter.svg", tsz,
                NbBundle.getMessage(UIBundle.class, "HINT_FilterMessages"), e -> {
            JPopupMenu popup = new JPopupMenu();
            for (String type : MessageFilterManager.getEffectiveMessageTypes()) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(type, !MessageFilterManager.isTypeHidden(type));
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
        return new JPopupMenu() {
            @Override
            public void show(Component invoker, int x, int y) {
                removeAll();
                buildToolBarItems(this);
                super.show(invoker, x, y);
            }
        };
    }

    private void buildToolBarItems(JPopupMenu popup) {
        JCheckBoxMenuItem editItem = new JCheckBoxMenuItem("Edit ToolBar", true);
        editItem.setEnabled(false);
        popup.add(editItem);
        popup.addSeparator();

        Object[][] buttons = {
            {hideBtn, PreferenceKeys.TOOLBAR_ARCHIVE, "Archive"},
            {newSessionBtn, PreferenceKeys.TOOLBAR_NEW_SESSION, "New Session"},
            {renameSessionBtn, PreferenceKeys.TOOLBAR_RENAME_SESSION, "Rename Session"},
            {refreshBtn, PreferenceKeys.TOOLBAR_RELOAD, "Reload"},
            {keepBtn, PreferenceKeys.TOOLBAR_KEEP, "Hide Older/Show All"},
            {toggleBlocksBtn, PreferenceKeys.TOOLBAR_EXPAND_COLLAPSE, "Expand/Collapse All"},
            {filterBtn, PreferenceKeys.TOOLBAR_FILTER, "Filter"},
            {exportBtn, PreferenceKeys.TOOLBAR_EXPORT, "Export"},
            {restartServerBtn, PreferenceKeys.TOOLBAR_RESTART, "Restart Harness"},
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
            boolean visible = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).getBoolean(
                (String) pair[1], isDefaultVisible((String) pair[1])
            );
            ((Component) pair[0]).setVisible(visible);
        }
    }

    @SuppressWarnings("unchecked")
    JComboBox<SessionItem> getSessionDropdown() {
        return (JComboBox<SessionItem>) sessionDropdown;
    }

    JButton getHideBtn() { return hideBtn; }

    private void fireNewSession() {
        var bridge = Lookup.getDefault().lookup(PlatformBridge.class);
        if (bridge == null) return;
        Project[] projects = bridge.projectContext().getAllOpenProjects();
        if (projects == null || projects.length == 0) {
            return;
        }
        if (projects.length == 1) {
            var sc = Lookup.getDefault()
                .lookup(SessionControl.class);
            if (sc != null) {
                sc.createNewSession(ProjectPaths.nativePath(projects[0]));
            }
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

    PermissionRequestPanel getPermissionPanel() { return permissionPanel; }
    PermissionRequestPanel getConfigConfirmPanel() { return configConfirmPanel; }

    JButton getSendBtn() { return sendBtn; }

    /**
     * Shows the resolved harness's icon on the Go button with a small
     * "Go" label below it, when an icon exists for the harness. Falls back
     * to the plain text label when the harness has no known icon.
     */
    private void applyHarnessIcon(JButton btn, String text) {
        Icon icon = ThemeManager.getIcon(HarnessCatalog.harnessIconName(BinaryResolver.resolveBinaryName()), 42);
        if (icon == null) {
            btn.setPreferredSize(new Dimension(96, 80));
            return;
        }
        btn.setIcon(icon);
        btn.setDisabledIcon(ImageUtilities.createDisabledIcon(icon));
        btn.setText(text);
        btn.setVerticalTextPosition(SwingConstants.BOTTOM);
        btn.setHorizontalTextPosition(SwingConstants.CENTER);
        btn.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, 12f));
        // Light top inset (~1/3 of the Go-label stack) so the icon isn't
        // shoved down. Grow the button if that stack is taller than 80px.
        int gap = btn.getIconTextGap();
        int textH = btn.getFontMetrics(btn.getFont()).getHeight();
        int bottom = 2;
        int top = Math.max(2, (bottom + textH + gap) / 3);
        btn.setMargin(new Insets(top, 12, bottom, 12));
        int height = top + icon.getIconHeight() + gap + textH + bottom;
        btn.setPreferredSize(new Dimension(96, Math.max(80, height)));
    }

    JButton getStopBtn() { return stopBtn; }

    JPanel getRightStatusPanel() { return rightStatusPanel; }

    ContextUsageMeter getContextUsageMeter() { return contextUsageMeter; }

    JButton getRestartServerBtn() { return restartServerBtn; }

    JButton getRefreshBtn() { return refreshBtn; }

    JButton getExportBtn() { return exportBtn; }

    /**
     * Applies the break-mode toggle. Arming it asks for confirmation first — the mode
     * approves every permission request with "allow once" and shows no prompt, so the
     * user must have seen that once. Releasing it never asks: going back to normal
     * handling is not the risky direction.
     */
    private void onBreakModeToggled(PulsingToggleButton btn) {
        if (btn.isSelected() && !confirmBreakMode()) {
            btn.setSelected(false);
            return;
        }
        PermissionAutoAllow.setEnabled(btn.isSelected());
        updateBreakModeTooltip(btn);
        if (btn.isSelected()) {
            // One confident beat on arming; the icon then stays still.
            btn.pulse();
        } else {
            btn.stopPulse();
        }
    }

    private boolean confirmBreakMode() {
        // The dialog takes focus; after it closes, X11 focus ownership can stay with the
        // disposed dialog window, so key events are dropped until the user clicks again.
        // Remember the pre-dialog focus owner and put it back when focus was lost.
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Component focusBefore = kfm.getFocusOwner();
        NotifyDescriptor.Confirmation confirm = new NotifyDescriptor.Confirmation(
                NbBundle.getMessage(UIBundle.class, "MSG_ConfirmBreakMode"),
                NbBundle.getMessage(UIBundle.class, "TITLE_ConfirmBreakMode"),
                NotifyDescriptor.YES_NO_OPTION,
                NotifyDescriptor.WARNING_MESSAGE);
        boolean confirmed = DialogDisplayer.getDefault().notify(confirm) == NotifyDescriptor.YES_OPTION;
        restoreFocusAfterDialog(focusBefore);
        return confirmed;
    }

    /**
     * Fallback used when the bundled guide cannot be opened: the custom URL when one is
     * configured, otherwise a status message. There is no default remote URL.
     */
    private Runnable guideFallback(String customGuideUrl) {
        if (customGuideUrl != null && !customGuideUrl.isBlank()) {
            return () -> BrowserUtils.openOrCopyUrl(customGuideUrl,
                    "STATUS_QuickstartCopied", (url, key) -> topComponent.setStatus(key, url));
        }
        return () -> topComponent.setStatus("STATUS_UserGuideUnavailable");
    }

    /**
     * Restores keyboard focus after a modal dialog. Only acts when focus is genuinely lost
     * ({@code null} or a component in a window that is no longer showing); an intact focus
     * owner is left alone so the dialog's own focus restoration wins.
     */
    private void restoreFocusAfterDialog(Component focusBefore) {
        SwingUtilities.invokeLater(() -> {
            Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (owner != null && owner.isShowing()) {
                return;
            }
            Component target = focusBefore != null && focusBefore.isShowing()
                    ? focusBefore : topComponent;
            target.requestFocusInWindow();
        });
    }

    private void updateBreakModeTooltip(JToggleButton btn) {
        String text = NbBundle.getMessage(UIBundle.class,
                btn.isSelected() ? "HINT_BreakModeOn" : "HINT_BreakModeOff");
        btn.setToolTipText(text);
        btn.getAccessibleContext().setAccessibleName(text);
        btn.getAccessibleContext().setAccessibleDescription(text);
    }

    /** Stops the debounce timer and unregisters the mini-assistant preference listener. */
    void cleanup() {
        newSessionDebounceTimer.stop();
        if (miniAssistantPrefListener != null) {
            NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                    .removePreferenceChangeListener(miniAssistantPrefListener);
            miniAssistantPrefListener = null;
        }
    }

    /**
     * Registers the mini-assistant preference listener so the header button
     * reacts to the "Enable Mini-Assistant" toggle. Called from
     * {@code AssistantTopComponent.addNotify()} so the listener lives exactly
     * as long as the component is attached, surviving close/reopen cycles.
     */
    void registerMiniAssistantPrefListener() {
        if (miniAssistantPrefListener != null || launchMiniAssistantBtn == null) {
            return;
        }
        miniAssistantPrefListener = evt -> {
            if (PreferenceKeys.MINI_ASSISTANT_ENABLED.equals(evt.getKey())) {
                boolean enabled = evt.getNewValue() == null || Boolean.parseBoolean(evt.getNewValue());
                SwingUtilities.invokeLater(() -> launchMiniAssistantBtn.setVisible(enabled));
            }
        };
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).addPreferenceChangeListener(miniAssistantPrefListener);
    }

    public static boolean isShowingHidden() {
        return PluginSettings.isShowHiddenSessions();
    }

    /**
     * Hides the given sessions, refresh the picker once, and close the mini
     * assistant only when nothing is left visible. {@code keepMiniAssistantOpen}
     * is true when a session (the active one) stays visible.
     */
    private void archiveSessions(List<String> sessionIds, boolean keepMiniAssistantOpen) {
        var archSc = Lookup.getDefault().lookup(SessionControl.class);
        if (archSc == null) {
            topComponent.setStatus("STATUS_ServiceUnavailable");
            return;
        }
        archSc.setHiddenBulk(sessionIds, true);
        archSc.refreshSessions();
        if (!keepMiniAssistantOpen && !isShowingHidden()) {
            MiniAssistantDialog.closeIfVisible();
        }
    }

}