package github.anandb.netbeans.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.contract.PinnedMessageControl;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.ProcessControl;
import org.openide.util.Lookup;
import github.anandb.netbeans.contract.UpdateDispatcher;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ConfigOptionConverter;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProcessService;
import github.anandb.netbeans.ui.platform.ProjectContext;
import github.anandb.netbeans.ui.platform.SessionService;
import org.netbeans.api.project.Project;
import org.openide.util.NbBundle;

/**
 * Handles all SessionListener callbacks, updating the chat panel, session dropdown,
 * status bar, and config controls. Replaces the corresponding methods in AssistantTopComponent.
 */
// DSL-CONTROLLER: not a view — session lifecycle bridge (refresh / load / new /
// rename / archive). Stays imperative; the toolbar buttons it toggles are
// kept imperative.
public class SessionLifecycleHandler implements SessionListener {

    private final SessionService sessionService = PlatformBridge.sessionServiceSafe();
    private final ProjectContext projectContext = PlatformBridge.projectContextSafe();
    private final ProcessService processService = PlatformBridge.processServiceSafe();

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
    // Dismisses stuck permission/config-confirm panels on session error (server
    // crash) so the chat input is never left disabled.
    private final Runnable pendingUiCleanup;

    // Shared mutable state
    private boolean optionsPanelCollapsed = true;
    private boolean isSwitchingSessionDropdown = false;
    private volatile boolean turnEnded = false;
    private Supplier<Boolean> onTurnEndedCallback;

    /** True while waiting for the preamble response on a new session.
     *  Keeps the progress bar visible until the preamble turn ends. */
    private volatile boolean pendingPreambleResponse = false;

    /** True while the WelcomeScreen (session-list view) is displayed instead of chat messages.
     *  Set when onSessionListUpdated shows the WelcomeScreen; cleared when a session is loaded.
     *  Used to restore the chat panel when archived sessions become visible via the toggle. */
    private boolean showingWelcomeScreen = false;

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
            Consumer<Boolean> optionsPanelToggler,
            Runnable pendingUiCleanup) {
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
        this.pendingUiCleanup = pendingUiCleanup;
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

    public void setOnTurnEndedCallback(Supplier<Boolean> callback) {
        this.onTurnEndedCallback = callback;
    }

    @Override
    public void onInternalMessageSent() {
        onNewMessageSent();
        SwingUtilities.invokeLater(() -> statusController.updateButtonState(true));
    }

    @Override
    public void onInternalMessageDone() {
        onMessageDone();
        SwingUtilities.invokeLater(() -> {
            statusController.updateButtonState(false);
            statusController.stopThinking();
        });
    }

    /** True once an end-of-turn signal arrived (SSE responding_finished/end_turn,
     *  RPC completion, or session load). Used by
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
        statusController.disarmRunWatchdog();
        runTurnEndedOffEdt();
    }

    /** Runs the state-machine turn-end transition off the EDT: SessionControl
     *  is a contract port that must stay free to do blocking work without
     *  freezing the UI thread. */
    private void runTurnEndedOffEdt() {
        CompletableFuture.runAsync(() -> sessionService.get().onTurnEnded());
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

        // Any inbound session/update for the CURRENT session proves the run is alive.
        // Updates from a switched-away session (SSE race guard, same check as
        // displayMessage) must not reset this run's stall clock.
        String updSessionId = update.params() != null ? update.params().sessionId() : null;
        if (updSessionId == null || updSessionId.equals(sessionService.get().getCurrentSessionId())) {
            statusController.touchRunActivity();
        }

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
                    MiniAssistantDialog.getInstance().onStreamUpdate(msg);
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
                // Persist so the value survives session reload
                String sessionId = update.params() != null ? update.params().sessionId() : null;
                if (sessionId != null) {
                    sessionService.get().setContextUsage(sessionId, used, size);
                }
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

        // End of turn signals: responding_finished/end_turn (also authoritative
        // via the RPC result's stopReason — see MessageSender).
        // NOTE: available_commands_update is deliberately NOT an end-of-turn
        // signal: queueing agents (goose) emit it at the START of a turn, and
        // treating it as turn-end set turnEnded=true mid-stream — the next user
        // message then bypassed the queue guard and hit goose while the first
        // prompt was still in flight, which drops the in-flight prompt (goose
        // returns no result for it) and wedges the session. For interleaved
        // agents it does prove the session is live, so it may clear the
        // pending-preamble wait early (progress bar + buffered messages) without
        // touching turnEnded.
        boolean endOfTurn = "responding_finished".equals(type) || "end_turn".equals(type);
        ProcessControl proc = processService != null ? processService.get() : null;
        boolean queueingAgent = proc != null && proc.getCapabilities().supportsMessageQueue();
        boolean preambleReady = "available_commands_update".equals(type) && !queueingAgent;

        if (endOfTurn) {
            LOG.fine("SSE turn-end signal received: type={0} (this confirms SSE path WORKS)", type);
            turnEnded = true;
        }

        // Preamble wait done: on end-of-turn for all agents, or early via
        // available_commands_update for interleaved agents.
        if ((endOfTurn || preambleReady) && pendingPreambleResponse) {
            pendingPreambleResponse = false;
            SwingUtilities.invokeLater(() -> {
                chatPanel.setSessionLoading(false);
                chatPanel.flushSessionBuffer();
            });
        }

        if (endOfTurn) {
            // Debounce finalization via the panel's shared flush timer (reset on
            // every processed message), so it fires 300ms after the last one drains.
            // Also re-enable send/toolbar here — the RPC result may be lost or
            // arrive late, and this SSE signal is the authoritative end of turn.
            SwingUtilities.invokeLater(() -> {
                chatPanel.restartFlushTimer();
                statusController.disarmRunWatchdog();
                // Always show Ready/Go. If messages were flushed,
                // a delayed timer will switch back to Sending/Stop.
                statusController.updateButtonState(false);
                statusController.stopThinking();
                if (onTurnEndedCallback != null) {
                    onTurnEndedCallback.get();
                }
            });
        }
    }

    @Override
    public void onSessionListUpdated(List<Session> allSessions) {
        // Precompute per-session preference lookups OFF the EDT: isHidden()/
        // getCustomTitle() hit NbPreferences once per session (and the legacy
        // migration path can even WRITE), so running them inside the EDT
        // runnable would stall the UI for long session lists. Only Swing
        // mutations happen on the EDT below.
        Map<String, Boolean> hiddenById = new HashMap<>();
        Map<String, String> titlesById = new HashMap<>();
        for (Session s : allSessions) {
            hiddenById.put(s.id(), sessionService.get().isHidden(s.id()));
            titlesById.put(s.id(), sessionService.get().getCustomTitle(s.id(), s.title()));
        }
        SwingUtilities.invokeLater(() -> {
            List<Session> sessions = allSessions;
            boolean showHidden = ChatLayoutBuilder.isShowingHidden();
            isSwitchingSessionDropdown = true;
            try {
                String currentId = sessionService.get().getCurrentSessionId();
                sessionDropdown.removeAllItems();
                LOG.fine("onSessionListUpdated: adding {0} sessions to dropdown", sessions.size());
                int selectIdx = -1;
                int itemIdx = 0;
                for (int i = 0; i < sessions.size(); i++) {
                    Session s = sessions.get(i);
                    // Filter hidden sessions unless show-hidden toggle is active
                    if (!showHidden && hiddenById.getOrDefault(s.id(), false)) {
                        continue;
                    }
                    String customTitle = titlesById.getOrDefault(s.id(), s.title());
                    sessionDropdown.addItem(new SessionItem(s, customTitle));
                    if (currentId != null && s.id().equals(currentId)) {
                        selectIdx = itemIdx;
                    }
                    itemIdx++;
                }

                boolean hasSessions = sessionDropdown.getItemCount() > 0;
                // Read the cached project list (NOT OpenProjects.getDefault().getOpenProjects(),
                // which blocks the EDT while projects load during startup/install).
                boolean hasProjects = projectContext.getAllOpenProjects().length > 0;
                hideBtn.setEnabled(currentId != null);
                // Only update icon here when the current session stays selected (selectIdx != -1).
                // When the session is being filtered out (archived), onSessionLoaded will set the
                // correct icon for the replacement session — updating here causes a brief icon flip.
                if (currentId != null && selectIdx != -1) {
                    boolean hidden = hiddenById.getOrDefault(currentId, false);
                    hideBtn.setIcon(ThemeManager.getIcon(hidden ? "unarchive.svg" : "archive.svg", PluginSettings.getToolbarIconSize()));
                    hideBtn.setToolTipText(hidden
                        ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_UnarchiveSession")
                        : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ArchiveSession"));
                }
                newSessionBtn.setEnabled(hasProjects);
                renameSessionBtn.setEnabled(hasSessions);

                configPanelController.ensureDefaultModelSelected();

                if (hasSessions) {
                    // Keep the current session selected even when it is archived while
                    // show-hidden is active: the user explicitly asked to view archived
                    // sessions, so forcing the selection away (and then falling through
                    // to a blank dropdown / WelcomeScreen when no non-archived session
                    // exists) made the dropdown render empty.
                    if (selectIdx != -1) {
                        sessionDropdown.setSelectedIndex(selectIdx);
                        // When transitioning from WelcomeScreen (no visible sessions) back to
                        // having sessions (e.g. show-archived toggled), load the auto-selected
                        // session to replace the WelcomeScreen with actual chat messages.
                        if (showingWelcomeScreen) {
                            showingWelcomeScreen = false;
                            sessionService.get().loadSession(currentId);
                        }
                    } else {
                        // Current session is gone (archived or project closed).
                        // Prefer a session from the SAME project as the last active
                        // session to avoid hijacking the user to a different project.
                        String prevDir = sessionService.get().getCurrentSessionDirectory();
                        SessionItem sameProjectMatch = null;
                        SessionItem mostRecentAny = sessionDropdown.getItemAt(0);
                        for (int i = 0; i < sessionDropdown.getItemCount(); i++) {
                            SessionItem item = sessionDropdown.getItemAt(i);
                            if (item != null && prevDir != null
                                    && prevDir.equals(item.getSession().effectiveDirectory())
                                    && (showHidden || !hiddenById.getOrDefault(item.getSession().id(), false))) {
                                sameProjectMatch = item;
                                break;
                            }
                        }
                        // Exclude archived sessions from the fallback unless the
                        // show-archived toggle is active.
                        SessionItem fallback = sameProjectMatch;
                        if (fallback == null) {
                            for (int i = 0; i < sessionDropdown.getItemCount(); i++) {
                                SessionItem item = sessionDropdown.getItemAt(i);
                                if (item != null && (showHidden || !hiddenById.getOrDefault(item.getSession().id(), false))) {
                                    fallback = item;
                                    break;
                                }
                            }
                        }
                        if (fallback != null) {
                            LOG.fine("Auto-selecting fallback session: {0} (sameProject={1})",
                                    fallback.getSession().id(), sameProjectMatch != null);
                            statusController.setInputEnabled(false);
                            sessionDropdown.setSelectedItem(fallback);
                            sessionService.get().loadSession(fallback.getSession().id());
                        } else {
                            // All remaining sessions are archived — show WelcomeScreen
                            showingWelcomeScreen = true;
                            chatPanel.setSessionList(sessions,
                                id -> sessionService.get().loadSession(id), () -> {
                                    Project[] projects = projectContext.getAllOpenProjects();
                                    if (projects == null || projects.length == 0) return;
                                    if (projects.length == 1) {
                                        sessionService.get().createNewSession(
                                            projects[0].getProjectDirectory().getPath());
                                    } else {
                                        projectPickerShower.accept(sessionDropdown);
                                    }
                                });
                            sessionDropdown.setSelectedIndex(-1);
                            statusController.setInputEnabled(false);
                            sessionStateHandler.accept(false);
                        }
                    }
                } else {
                    showingWelcomeScreen = true;
                    // Filter hidden sessions for WelcomeScreen too
                    List<Session> visibleSessions = showHidden ? sessions
                        : sessions.stream()
                            .filter(s -> !hiddenById.getOrDefault(s.id(), false))
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
        showingWelcomeScreen = false;
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
    public void onPreambleStarted() {
        SwingUtilities.invokeLater(() -> {
            chatPanel.addMessage(ProcessedMessage.createInfo("Posting preamble..."));
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
            statusController.disarmRunWatchdog();
            statusController.stopThinking();
            // Restore persisted context usage tooltip
            String usage = sessionService.get().getContextUsage(sessionId);
            if (usage != null) {
                String[] parts = usage.split(",");
                if (parts.length == 2) {
                    try {
                        long used = Long.parseLong(parts[0]);
                        long size = Long.parseLong(parts[1]);
                        statusController.setTooltip("HINT_ContextUsage", used, size);
                    } catch (NumberFormatException e) {
                        // ignore malformed data
                    }
                }
            }
            cwdLabelUpdater.accept(null);
            // Set sessionActive BEFORE updateButtonState: updateButtonState now
            // derives the Go button state from sessionActive directly.
            sessionStateHandler.accept(true);
            statusController.updateButtonState(false);
            statusController.setInputEnabled(true);
            hideBtn.setEnabled(true);
            // Fall back to models/modes from cached session if configOptions is null
            // (Claude sends models/modes in session/new, not session/load)
            List<SessionConfigOption> resolvedConfigOptions = configOptions;
            if (resolvedConfigOptions == null) {
                Session cachedSession = sessionService.get().getSession(sessionId);
                if (cachedSession != null) {
                    resolvedConfigOptions = ConfigOptionConverter.fromModelsAndModes(
                            cachedSession.models(), cachedSession.modes());
                }
            }
            if (resolvedConfigOptions != null) {
                configPanelController.updateConfigControls(resolvedConfigOptions, isStartup);
            }
            // If this is a new session (isStartup=true), apply any pre-selected config values
            // from the config panel that the user may have set before creating the chat
            if (isStartup) {
                configPanelController.applyPreSelectedConfigValues(sessionId, resolvedConfigOptions);
            }

            boolean hidden = sessionService.get().isHidden(sessionId);
            hideBtn.setIcon(ThemeManager.getIcon(hidden ? "unarchive.svg" : "archive.svg", PluginSettings.getToolbarIconSize()));
            hideBtn.setToolTipText(hidden
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_UnarchiveSession")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ArchiveSession"));
            // Sync the dropdown to the loaded session so it always reflects what's displayed.
            boolean found = false;
            for (int i = 0; i < sessionDropdown.getItemCount(); i++) {
                SessionItem item = sessionDropdown.getItemAt(i);
                if (item != null && sessionId.equals(item.getSession().id())) {
                    sessionDropdown.setSelectedItem(item);
                    found = true;
                    break;
                }
            }
            // New session not in dropdown yet (refreshSessions was deferred post-preamble).
            // Add it from cache so the dropdown shows the correct session immediately.
            if (!found && isStartup) {
                Session cached = sessionService.get().getSession(sessionId);
                if (cached != null) {
                    String customTitle = sessionService.get().getCustomTitle(sessionId, cached.title());
                    SessionItem newItem = new SessionItem(cached, customTitle);
                    sessionDropdown.insertItemAt(newItem, 0);
                    sessionDropdown.setSelectedIndex(0);
                }
            }
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
        SwingUtilities.invokeLater(() -> {
            inputArea.setText("");
            cwdLabelUpdater.accept("");
            MiniAssistantDialog.getInstance().dispose();
        });
    }

    @Override
    public void onSessionError(String message) {
        SwingUtilities.invokeLater(() -> {
            statusController.setStatus("STATUS_Error", message);
            statusController.stopThinking();
            statusController.disarmRunWatchdog();
            statusController.updateButtonState(false);
            statusController.setInputEnabled(true);
            turnEnded = true;
            runTurnEndedOffEdt();
            chatPanel.setSessionLoading(false);
            chatPanel.stopStreaming();
            chatPanel.addMessage(ProcessedMessage.createError(
                MessageType.error_response,
                NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Error", message),
                null, null
            ));
            // On server crash/error, dismiss any stuck permission/config-confirm
            // panel so the chat input is re-enabled and not left disabled.
            if (pendingUiCleanup != null) {
                pendingUiCleanup.run();
            }
        });
    }
}