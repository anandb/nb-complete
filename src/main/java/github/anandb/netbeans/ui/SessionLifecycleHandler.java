package github.anandb.netbeans.ui;

import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.contract.PinnedMessageControl;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.contract.SessionControl;
import org.openide.util.Lookup;
import github.anandb.netbeans.contract.UpdateDispatcher;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProjectContext;
import github.anandb.netbeans.ui.platform.SessionService;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.util.NbBundle;

/**
 * Handles all SessionListener callbacks, updating the chat panel, session dropdown,
 * status bar, and config controls. Replaces the corresponding methods in AssistantTopComponent.
 */
// DSL-CONTROLLER: not a view — session lifecycle bridge (refresh / load / new /
// rename / archive). Stays imperative; the toolbar buttons it toggles are
// kept imperative.
public class SessionLifecycleHandler implements SessionListener {

    private final SessionService sessionService = Lookup.getDefault().lookup(PlatformBridge.class).sessionService();
    private final ProjectContext projectContext = Lookup.getDefault().lookup(PlatformBridge.class).projectContext();

    private static final Logger LOG = Logger.from(SessionLifecycleHandler.class);

    private final ChatThreadPanel chatPanel;
    private final JComboBox<SessionItem> sessionDropdown;
    private final JButton hideBtn;
    private final JButton newSessionBtn;
    private final JButton renameSessionBtn;
    private final JButton toggleOptionsBtn;
    private final ConfigPanelController configPanelController;
    private final PlaceholderTextArea inputArea;
    private final StatusController statusController;

    // Callbacks to AssistantTopComponent for operations kept there
    private final Consumer<JComponent> projectPickerShower;
    private final Consumer<String> tabNameUpdater;
    private final Consumer<String> cwdLabelUpdater;
    private final Consumer<Boolean> sessionStateHandler;
    private final Consumer<Boolean> optionsPanelToggler;

    // Shared mutable state
    private boolean optionsPanelCollapsed = true;
    private boolean isSwitchingSessionDropdown = false;
    private volatile boolean turnEnded = false;

    /** True while waiting for the preamble response on a new session.
     *  Keeps the progress bar visible until the preamble turn ends. */
    private volatile boolean pendingPreambleResponse = false;

    public SessionLifecycleHandler(
            ChatThreadPanel chatPanel,
            JComboBox<SessionItem> sessionDropdown,
            JButton hideBtn,
            JButton newSessionBtn,
            JButton renameSessionBtn,
            JButton toggleOptionsBtn,
            ConfigPanelController configPanelController,
            PlaceholderTextArea inputArea,
            StatusController statusController,
            Consumer<JComponent> projectPickerShower,
            Consumer<String> tabNameUpdater,
            Consumer<String> cwdLabelUpdater,
            Consumer<Boolean> sessionStateHandler,
            Consumer<Boolean> optionsPanelToggler) {
        this.chatPanel = chatPanel;
        this.sessionDropdown = sessionDropdown;
        this.hideBtn = hideBtn;
        this.newSessionBtn = newSessionBtn;
        this.renameSessionBtn = renameSessionBtn;
        this.toggleOptionsBtn = toggleOptionsBtn;
        this.configPanelController = configPanelController;
        this.inputArea = inputArea;
        this.statusController = statusController;
        this.projectPickerShower = projectPickerShower;
        this.tabNameUpdater = tabNameUpdater;
        this.cwdLabelUpdater = cwdLabelUpdater;
        this.sessionStateHandler = sessionStateHandler;
        this.optionsPanelToggler = optionsPanelToggler;
    }

    boolean isOptionsPanelCollapsed() {
        return optionsPanelCollapsed;
    }

    void setOptionsPanelCollapsed(boolean collapsed) {
        optionsPanelCollapsed = collapsed;
    }

    /** Reset turn-ended flag when a new message is sent. */
    public void onNewMessageSent() {
        turnEnded = false;
    }

    /** True once an end-of-turn signal arrived (SSE responding_finished/end_turn/
     *  available_commands_update, RPC completion, or session load). Used by
     *  {@code ChatThreadPanel.flushTimer} to gate idle-gap-based finalization
     *  on the turn actually being over. */
    public boolean isTurnEnded() {
        return turnEnded;
    }

    /** Signal that the RPC completed (turn ended). Prevents displayMessage
     *  from overriding the button state with late SSE messages, and
     *  recovers the state machine if it was stuck at STOPPING. */
    public void onMessageDone() {
        LOG.info("onMessageDone called (turnEnded -> true, triggering onTurnEnded)");
        turnEnded = true;
        sessionService.get().onTurnEnded();
    }

    boolean isSwitchingSessionDropdown() {
        return isSwitchingSessionDropdown;
    }

    void setSwitchingSessionDropdown(boolean switching) {
        isSwitchingSessionDropdown = switching;
    }

    // ---------------------------------------------------------------
    // SessionListener implementation
    // ---------------------------------------------------------------

    @Override
    public void onSessionUpdate(SessionUpdate update) {
        String type = update.update() != null && update.update().type() != null ? update.update().type().name() : null;
        String msgId = update.update() != null ? update.update().messageId() : null;
        LOG.fine("UI received session update: type={0}, msgId={1}", type, msgId);

        Lookup.getDefault().lookup(UpdateDispatcher.class).handle(update, new UIHandler() {
            @Override
            public void displayMessage(ProcessedMessage msg) {
                String msgSessionId = update.params() != null ? update.params().sessionId() : null;
                SwingUtilities.invokeLater(() -> {
                    // Guard: user may have switched sessions between the SSE
                    // check in handleSseUpdate and EDT execution. Dropping a
                    // late message is safe; showing it in the wrong session is
                    // a visible data corruption.
                    if (msgSessionId != null) {
                        String currentId = sessionService.get().getCurrentSessionId();
                        if (!msgSessionId.equals(currentId)) {
                            return;
                        }
                    }
                    chatPanel.addMessage(msg);
                    // Capture turnEnded once to avoid TOCTOU: the volatile
                    // field can be set to true by onMessageDone() or an SSE
                    // turn-end signal between our read and the button update,
                    // causing a brief flicker (button → responding → idle).
                    boolean ended = turnEnded;
                    if (!ended) {
                        statusController.updateButtonState(true);
                    }
                });
            }

            @Override
            public void updateConfig(List<SessionConfigOption> options) {
                if (options != null) {
                    configPanelController.updateConfigControls(options);
                }
            }

            @Override
            public void refreshSessions() {
                sessionService.get().refreshSessions();
            }

            @Override
            public void updateUsage(long used, long size) {
                SwingUtilities.invokeLater(() ->
                    statusController.setTooltip("HINT_ContextUsage", used, size)
                );
            }
        });

        // Status updates
        Boolean isThinking = update.isThinking();
        if (isThinking != null) {
            SwingUtilities.invokeLater(() -> {
                if (isThinking) {
                    statusController.setStatus("STATUS_Thinking");
                    statusController.startThinking();
                } else {
                    String current = statusController.getStatusText();
                    String thinking = NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Thinking");
                    if (current != null && current.contains(thinking.replace(".", "").trim())) {
                        statusController.setStatus("STATUS_Responding");
                    }
                }
            });
        }

        // End of turn signals
        if ("responding_finished".equals(type) || "end_turn".equals(type)
                || "available_commands_update".equals(type)) {
            LOG.fine("SSE turn-end signal received: type={0} (this confirms SSE path WORKS)", type);
            turnEnded = true;
            // If waiting for the preamble response, hide the progress bar now.
            if (pendingPreambleResponse) {
                pendingPreambleResponse = false;
                SwingUtilities.invokeLater(() -> {
                    chatPanel.setSessionLoading(false);
                    chatPanel.flushSessionBuffer();
                });
            }
            // Debounce finalization via the panel's shared flush timer (reset on
            // every processed message), so it fires 300ms after the last one drains.
            SwingUtilities.invokeLater(chatPanel::restartFlushTimer);
        }
    }

    @Override
    public void onSessionListUpdated(List<Session> allSessions) {
        SwingUtilities.invokeLater(() -> {
            List<Session> sessions = allSessions;
            isSwitchingSessionDropdown = true;
            try {
                String currentId = sessionService.get().getCurrentSessionId();
                sessionDropdown.removeAllItems();
                LOG.fine("onSessionListUpdated: adding {0} sessions to dropdown", sessions.size());
                int selectIdx = -1;
                int itemIdx = 0;
                boolean showHidden = ChatLayoutBuilder.isShowingHidden();
                for (int i = 0; i < sessions.size(); i++) {
                    Session s = sessions.get(i);
                    // Filter hidden sessions unless show-hidden toggle is active
                    if (!showHidden && sessionService.get().isHidden(s.id())) {
                        continue;
                    }
                    String customTitle = sessionService.get().getCustomTitle(s.id(), s.title());
                    sessionDropdown.addItem(new SessionItem(s, customTitle));
                    if (currentId != null && s.id().equals(currentId)) {
                        selectIdx = itemIdx;
                    }
                    itemIdx++;
                }

                boolean hasSessions = sessionDropdown.getItemCount() > 0;
                boolean hasProjects = OpenProjects.getDefault().getOpenProjects().length > 0;
                hideBtn.setEnabled(currentId != null);
                // Only update icon here when the current session stays selected (selectIdx != -1).
                // When the session is being filtered out (archived), onSessionLoaded will set the
                // correct icon for the replacement session — updating here causes a brief icon flip.
                if (currentId != null && selectIdx != -1) {
                    boolean hidden = sessionService.get().isHidden(currentId);
                    hideBtn.setIcon(ThemeManager.getIcon(hidden ? "unarchive.svg" : "archive.svg", PluginSettings.getToolbarIconSize()));
                    hideBtn.setToolTipText(hidden
                        ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_UnarchiveSession")
                        : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ArchiveSession"));
                }
                newSessionBtn.setEnabled(hasProjects);
                renameSessionBtn.setEnabled(hasSessions);

                configPanelController.ensureDefaultModelSelected();

                if (hasSessions) {
                    if (selectIdx != -1) {
                        sessionDropdown.setSelectedIndex(selectIdx);
                    } else {
                        // Current session is gone (e.g. its project closed).
                        // Prefer a session from the SAME project as the last active
                        // session to avoid hijacking the user to a different project.
                        String prevDir = sessionService.get().getCurrentSessionDirectory();
                        SessionItem sameProjectMatch = null;
                        SessionItem mostRecentAny = sessionDropdown.getItemAt(0);
                        for (int i = 0; i < sessionDropdown.getItemCount(); i++) {
                            SessionItem item = sessionDropdown.getItemAt(i);
                            if (item != null && prevDir != null
                                    && prevDir.equals(item.getSession().effectiveDirectory())) {
                                sameProjectMatch = item;
                                break;
                            }
                        }
                        SessionItem fallback = sameProjectMatch != null ? sameProjectMatch : mostRecentAny;
                        if (fallback != null) {
                            LOG.fine("Auto-selecting fallback session: {0} (sameProject={1})",
                                    fallback.getSession().id(), sameProjectMatch != null);
                            sessionService.get().loadSession(fallback.getSession().id());
                        }
                    }
                } else {
                    // Filter hidden sessions for WelcomeScreen too
                    List<Session> visibleSessions = showHidden ? sessions
                        : sessions.stream()
                            .filter(s -> !sessionService.get().isHidden(s.id()))
                            .toList();
                    chatPanel.setSessionList(visibleSessions, id -> sessionService.get().loadSession(id), () -> {
                        Project[] projects = projectContext.getAllOpenProjects();
                        if (projects == null || projects.length == 0) {
                            return;
                        }
                        if (projects.length == 1) {
                            sessionService.get().createNewSession(projects[0].getProjectDirectory().getPath());
                        } else {
                            projectPickerShower.accept(sessionDropdown);
                        }
                    });
                    sessionDropdown.setSelectedIndex(-1);
                    if (!hasProjects) {
                        statusController.setStatus("STATUS_OpenProject");
                    } else {
                        statusController.setStatus("STATUS_NewChat");
                    }
                    optionsPanelToggler.accept(true);
                    statusController.setInputEnabled(false);
                    sessionStateHandler.accept(false);
                    configPanelController.ensureDefaultModelAdded();
                }
            } finally {
                isSwitchingSessionDropdown = false;
                sessionDropdown.revalidate();
                sessionDropdown.repaint();
            }
        });
    }

    @Override
    public void onSessionStarted(String sessionId) {
        SwingUtilities.invokeLater(() -> {
            chatPanel.setSessionId(sessionId);
            // Prime the pinned-message cache before any bubbles are created.
            PinnedMessageControl pinStore = Lookup.getDefault()
                    .lookup(PinnedMessageControl.class);
            if (pinStore != null && sessionId != null) {
                pinStore.loadSession(sessionId);
            }
            chatPanel.clearMessages();
            chatPanel.setSessionLoading(true);
            chatPanel.setSessionProgress(10);
            if (sessionId == null) {
                pendingPreambleResponse = true;
                statusController.setStatus("STATUS_CreatingSession");
                tabNameUpdater.accept(null);
            } else {
                statusController.setStatus("STATUS_LoadingChat");
            }
        });
    }

    @Override
    public void onPreambleDone() {
        SwingUtilities.invokeLater(() -> {
            if (pendingPreambleResponse) {
                pendingPreambleResponse = false;
                chatPanel.setSessionLoading(false);
            }
        });
    }

    @Override
    public void onSessionRenamed(String sessionId) {
        SwingUtilities.invokeLater(() -> {
            SessionControl sc = sessionService.get();
            for (int i = 0; i < sessionDropdown.getItemCount(); i++) {
                SessionItem item = sessionDropdown.getItemAt(i);
                if (item != null && sessionId.equals(item.getSession().id())) {
                    String newTitle = sc.getCustomTitle(sessionId, item.getSession().title());
                    SessionItem updated = new SessionItem(item.getSession(), newTitle);
                    sessionDropdown.insertItemAt(updated, i);
                    sessionDropdown.removeItemAt(i + 1);
                    // Keep the selection if it was this item
                    if (sessionDropdown.getSelectedIndex() == i) {
                        sessionDropdown.setSelectedItem(updated);
                    }
                    break;
                }
            }
            sessionDropdown.revalidate();
            sessionDropdown.repaint();
        });
    }

    @Override
    public void onSessionProgress(int percent) {
        chatPanel.setSessionProgress(percent);
    }

    @Override
    public void onSessionLoaded(String sessionId, List<SessionConfigOption> configOptions, boolean isStartup) {
        SwingUtilities.invokeLater(() -> {
            chatPanel.setSessionId(sessionId);
            // Prime the pinned-message cache so bubble constructors see stored pins.
            PinnedMessageControl pinStore = Lookup.getDefault()
                    .lookup(PinnedMessageControl.class);
            if (pinStore != null) {
                pinStore.loadSession(sessionId);
            }
            // The session/load response with configOptions signals end of turn.
            // Set turnEnded immediately so late SSE messages don't call
            // updateButtonState(true), but defer stopStreaming via a flush timer
            // to allow any in-flight SSE delta to arrive first.
            turnEnded = true;
            // Debounce finalization via the panel's shared flush timer, so it
            // fires 300ms after the last reloaded message drains from the queue.
            chatPanel.restartFlushTimer();
            statusController.setStatus("STATUS_Ready");
            statusController.stopThinking();
            statusController.updateButtonState(false);
            cwdLabelUpdater.accept(null);
            if (configOptions != null) {
                configPanelController.updateConfigControls(configOptions, isStartup);
            }
            // If this is a new session (isStartup=true), apply any pre-selected config values
            // from the config panel that the user may have set before creating the chat
            if (isStartup) {
                configPanelController.applyPreSelectedConfigValues(sessionId, configOptions);
            }

            statusController.setInputEnabled(true);
            sessionStateHandler.accept(true);
            hideBtn.setEnabled(true);
            boolean hidden = sessionService.get().isHidden(sessionId);
            hideBtn.setIcon(ThemeManager.getIcon(hidden ? "unarchive.svg" : "archive.svg", PluginSettings.getToolbarIconSize()));
            hideBtn.setToolTipText(hidden
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_UnarchiveSession")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ArchiveSession"));
            if (!sessionDropdown.isPopupVisible()) {
                inputArea.requestFocusInWindow();
            }
            chatPanel.scrollToBottom();
            if (!pendingPreambleResponse) {
                chatPanel.setSessionLoading(false);
                // For startup sessions the flush already happened in onPreambleDone/responding_finished.
                if (!isStartup) {
                    chatPanel.flushSessionBuffer();
                }
            }
        });
    }

    @Override
    public void onSessionLoading(boolean isLoading) {
        SwingUtilities.invokeLater(() -> statusController.setInputEnabled(!isLoading));
    }

    @Override
    public void onAllProjectsClosed() {
        SwingUtilities.invokeLater(() -> inputArea.setText(""));
    }

    @Override
    public void onSessionError(String message) {
        SwingUtilities.invokeLater(() -> {
            statusController.setStatus("STATUS_Error", message);
            statusController.stopThinking();
            chatPanel.setSessionLoading(false);
            chatPanel.stopStreaming();
            chatPanel.addMessage(ProcessedMessage.createError(
                MessageType.error_response,
                NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Error", message),
                null, null
            ));
        });
    }
}
