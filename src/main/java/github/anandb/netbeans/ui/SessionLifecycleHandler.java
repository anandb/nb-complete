package github.anandb.netbeans.ui;

import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.contract.SessionControl;
import org.openide.util.Lookup;
import github.anandb.netbeans.contract.UpdateDispatcher;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.project.ACPProjectManager;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.TimingConstants;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.util.NbBundle;

/**
 * Handles all SessionListener callbacks, updating the chat panel, session dropdown,
 * status bar, and config controls. Replaces the corresponding methods in AssistantTopComponent.
 */
public class SessionLifecycleHandler implements SessionListener {

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

    // Shared mutable state
    private boolean optionsPanelCollapsed = true;
    private boolean isSwitchingSessionDropdown = false;
    private volatile boolean turnEnded = false;

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
            Consumer<String> cwdLabelUpdater) {
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

    /** Signal that the RPC completed (turn ended). Prevents displayMessage
     *  from overriding the button state with late SSE messages, and
     *  recovers the state machine if it was stuck at STOPPING. */
    public void onMessageDone() {
        LOG.info("onMessageDone called (turnEnded -> true, triggering onTurnEnded)");
        turnEnded = true;
        Lookup.getDefault().lookup(SessionControl.class).onTurnEnded();
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
                SwingUtilities.invokeLater(() -> {
                    chatPanel.addMessage(msg);
                    if (!turnEnded) {
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
                Lookup.getDefault().lookup(SessionControl.class).refreshSessions();
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
            LOG.info("SSE turn-end signal received: type={0} (this confirms SSE path WORKS)", type);
            turnEnded = true;
            // Brief delay to allow any in-flight delta notifications to arrive
            // before finalizing the stream bubble.
            Timer flushTimer = new Timer(TimingConstants.STREAM_FLUSH_MS, e -> {
                if (chatPanel.isDisplayable()) {
                    chatPanel.stopStreaming();
                }
            });
            flushTimer.setRepeats(false);
            flushTimer.start();
        }
    }

    @Override
    public void onSessionListUpdated(List<Session> allSessions) {
        SwingUtilities.invokeLater(() -> {
            List<Session> sessions = allSessions;
            isSwitchingSessionDropdown = true;
            try {
                String currentId = Lookup.getDefault().lookup(SessionControl.class).getCurrentSessionId();
                sessionDropdown.removeAllItems();
                LOG.fine("onSessionListUpdated: adding {0} sessions to dropdown", sessions.size());
                int selectIdx = -1;
                int itemIdx = 0;
                boolean showHidden = ChatLayoutBuilder.isShowingHidden();
                for (int i = 0; i < sessions.size(); i++) {
                    Session s = sessions.get(i);
                    // Filter hidden sessions unless show-hidden toggle is active
                    if (!showHidden && Lookup.getDefault().lookup(SessionControl.class).isHidden(s.id())) {
                        continue;
                    }
                    String customTitle = Lookup.getDefault().lookup(SessionControl.class).getCustomTitle(s.id(), s.title());
                    sessionDropdown.addItem(new SessionItem(s, customTitle));
                    if (currentId != null && s.id().equals(currentId)) {
                        selectIdx = itemIdx;
                    }
                    itemIdx++;
                }

                boolean hasSessions = sessionDropdown.getItemCount() > 0;
                boolean hasProjects = OpenProjects.getDefault().getOpenProjects().length > 0;
                hideBtn.setEnabled(currentId != null);
                if (currentId != null) {
                    boolean hidden = Lookup.getDefault().lookup(SessionControl.class).isHidden(currentId);
                    hideBtn.setIcon(ThemeManager.getIcon(hidden ? "unarchive.svg" : "archive.svg", 28));
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
                        SessionItem cur = sessionDropdown.getItemAt(selectIdx);
                        if (cur != null) {
                            LOG.fine("Re-loading current session: {0}", cur.getSession().id());
                            Lookup.getDefault().lookup(SessionControl.class).loadSession(cur.getSession().id());
                        }
                    } else {
                        SessionItem mostRecent = sessionDropdown.getItemAt(0);
                        if (mostRecent != null) {
                            LOG.fine("Loading most recent session: {0}", mostRecent.getSession().id());
                            Lookup.getDefault().lookup(SessionControl.class).loadSession(mostRecent.getSession().id());
                        }
                    }
                } else {
                    // Filter hidden sessions for WelcomeScreen too
                    List<Session> visibleSessions = showHidden ? sessions
                        : sessions.stream()
                            .filter(s -> !Lookup.getDefault().lookup(SessionControl.class).isHidden(s.id()))
                            .toList();
                    chatPanel.setSessionList(visibleSessions, id -> Lookup.getDefault().lookup(SessionControl.class).loadSession(id), () -> {
                        Project[] projects = ACPProjectManager.getInstance().getAllOpenProjects();
                        if (projects == null || projects.length == 0) {
                            return;
                        }
                        if (projects.length == 1) {
                            Lookup.getDefault().lookup(SessionControl.class).createNewSession(projects[0].getProjectDirectory().getPath());
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
                    optionsPanelCollapsed = false;
                    configPanelController.getComponent().setVisible(true);
                    toggleOptionsBtn.setIcon(ThemeManager.getIcon("arrow-down.svg", 25));
                    statusController.setInputEnabled(false);
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
            chatPanel.clearMessages();
            if (sessionId == null) {
                statusController.setStatus("STATUS_CreatingSession");
                tabNameUpdater.accept(null);
            } else {
                statusController.setStatus("STATUS_LoadingChat");
            }
        });
    }

    @Override
    public void onSessionLoaded(String sessionId, List<SessionConfigOption> configOptions, boolean isStartup) {
        SwingUtilities.invokeLater(() -> {
            // The session/load response with configOptions signals end of turn.
            // Set turnEnded immediately so late SSE messages don't call
            // updateButtonState(true), but defer stopStreaming via a flush timer
            // to allow any in-flight SSE delta to arrive first.
            turnEnded = true;
            Timer flushTimer = new Timer(TimingConstants.STREAM_FLUSH_MS, e -> {
                if (chatPanel.isDisplayable()) {
                    chatPanel.stopStreaming();
                }
            });
            flushTimer.setRepeats(false);
            flushTimer.start();
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
            hideBtn.setEnabled(true);
            boolean hidden = Lookup.getDefault().lookup(SessionControl.class).isHidden(sessionId);
            hideBtn.setIcon(ThemeManager.getIcon(hidden ? "unarchive.svg" : "archive.svg", 28));
            hideBtn.setToolTipText(hidden
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_UnarchiveSession")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ArchiveSession"));
            if (!sessionDropdown.isPopupVisible()) {
                inputArea.requestFocusInWindow();
            }
            chatPanel.scrollToBottom();
        });
    }

    @Override
    public void onSessionLoading(boolean isLoading) {
        SwingUtilities.invokeLater(() -> statusController.setInputEnabled(!isLoading));
    }

    @Override
    public void onSessionError(String message) {
        SwingUtilities.invokeLater(() -> {
            statusController.setStatus("STATUS_Error", message);
            statusController.stopThinking();
            chatPanel.stopStreaming();
            chatPanel.addMessage(ProcessedMessage.createError(
                MessageType.error_response,
                NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Error", message),
                null, null
            ));
        });
    }
}
