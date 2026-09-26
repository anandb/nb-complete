package github.anandb.netbeans.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProjectContext;
import github.anandb.netbeans.ui.platform.SessionService;

/**
 * Headless regression tests for {@link SessionLifecycleHandler#onSessionListUpdated}.
 * Verifies the session dropdown never renders blank after the show-archived (eye)
 * toggle when the current session is archived.
 */
class SessionLifecycleHandlerTest {

    private static final String SHOW_HIDDEN_KEY = "showHiddenSessions";

    private MockedStatic<PlatformBridge> platformBridgeMock;
    private MockedStatic<ThemeManager> themeManagerMock;
    private MockedStatic<PluginSettings> pluginSettingsMock;
    /** Original raw value of {@link #SHOW_HIDDEN_KEY}; null when unset. Restored in tearDown. */
    private String originalShowHidden;

    private SessionControl sessionControl;
    private ProjectContext projectContext;
    private ChatThreadPanel chatPanel;
    private StatusController statusController;
    private ConfigPanelController configPanelController;
    private PlaceholderTextArea inputArea;
    private UIUtils.WrappingComboBox<SessionItem> sessionDropdown;
    private JButton hideBtn;
    private JButton newSessionBtn;
    private JButton renameSessionBtn;
    private JButton toggleOptionsBtn;

    private void setUpMocks(boolean showHidden) {
        platformBridgeMock = mockStatic(PlatformBridge.class);
        themeManagerMock = mockStatic(ThemeManager.class);
        pluginSettingsMock = mockStatic(PluginSettings.class);

        sessionControl = mock(SessionControl.class);
        projectContext = mock(ProjectContext.class);
        chatPanel = mock(ChatThreadPanel.class);
        statusController = mock(StatusController.class);
        configPanelController = mock(ConfigPanelController.class);
        inputArea = mock(PlaceholderTextArea.class);
        sessionDropdown = new UIUtils.WrappingComboBox<>();
        hideBtn = new JButton();
        newSessionBtn = new JButton();
        renameSessionBtn = new JButton();
        toggleOptionsBtn = new JButton();

        // Capture the developer's real value first: NbPreferences writes hit the
        // module's userdir, so the override must be undone in tearDown.
        originalShowHidden = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get(SHOW_HIDDEN_KEY, null);
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(SHOW_HIDDEN_KEY, showHidden);
        when(projectContext.getAllOpenProjects()).thenReturn(new org.netbeans.api.project.Project[0]);

        platformBridgeMock.when(PlatformBridge::sessionServiceSafe)
                .thenReturn((SessionService) () -> sessionControl);
        platformBridgeMock.when(PlatformBridge::projectContextSafe)
                .thenReturn(projectContext);
        themeManagerMock.when(() -> ThemeManager.getIcon(anyString(), anyInt()))
                .thenReturn(null);
        pluginSettingsMock.when(PluginSettings::getToolbarIconSize).thenReturn(16);
    }

    @AfterEach
    void tearDown() {
        if (platformBridgeMock != null) platformBridgeMock.close();
        if (themeManagerMock != null) themeManagerMock.close();
        if (pluginSettingsMock != null) pluginSettingsMock.close();
        restoreShowHiddenPref();
    }

    /**
     * Puts the show-archived preference back the way this machine had it.
     * {@code NbPreferences} writes hit the module's real userdir, so without
     * this the test run permanently flips the developer's own setting.
     */
    private void restoreShowHiddenPref() {
        if (originalShowHidden == null) {
            return;
        }
        java.util.prefs.Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        if ("true".equals(originalShowHidden)) {
            prefs.putBoolean(SHOW_HIDDEN_KEY, true);
        } else if ("false".equals(originalShowHidden)) {
            prefs.putBoolean(SHOW_HIDDEN_KEY, false);
        } else {
            prefs.remove(SHOW_HIDDEN_KEY);
        }
        originalShowHidden = null;
    }

    private SessionLifecycleHandler newHandler() {
        Consumer<JComponent> projectPickerShower = c -> { };
        Consumer<String> tabNameUpdater = s -> { };
        Consumer<String> cwdLabelUpdater = s -> { };
        Consumer<Boolean> sessionStateHandler = b -> { };
        Consumer<Boolean> optionsPanelToggler = b -> { };
        Runnable pendingUiCleanup = () -> { };
        return new SessionLifecycleHandler(
                chatPanel, sessionDropdown, hideBtn, newSessionBtn, renameSessionBtn,
                toggleOptionsBtn, configPanelController, inputArea, statusController,
                projectPickerShower, tabNameUpdater, cwdLabelUpdater,
                sessionStateHandler, optionsPanelToggler, pendingUiCleanup);
    }

    private Session session(String id, boolean hidden) {
        when(sessionControl.isHidden(id)).thenReturn(hidden);
        return new Session(id, "Session " + id, "/tmp/" + id, null, null, "2026-01-01T00:00:00Z", null, null, null, null);
    }

    private List<Session> sessions(Session... s) {
        List<Session> list = new ArrayList<>();
        java.util.Collections.addAll(list, s);
        return list;
    }

    private void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private void stubCurrent(String currentId) {
        when(sessionControl.getCurrentSessionId()).thenReturn(currentId);
    }

    @Test
    void showArchivedKeepsArchivedCurrentSessionSelected() throws Exception {
        setUpMocks(true);
        stubCurrent("arch-1");
        List<Session> list = sessions(session("arch-1", true), session("arch-2", true));
        when(sessionControl.getCustomTitle(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));

        SessionLifecycleHandler handler = newHandler();
        handler.onSessionListUpdated(list);
        flushEdt();

        assertEquals(2, sessionDropdown.getItemCount(), "All archived sessions must be listed");
        assertNotEquals(-1, sessionDropdown.getSelectedIndex(), "Dropdown must not render blank");
        assertNotNull(sessionDropdown.getSelectedItem());
        assertEquals("arch-1",
                ((SessionItem) sessionDropdown.getSelectedItem()).getSession().id(),
                "Archived current session must stay selected while show-archived is on");
    }

    @Test
    void showArchivedFallbackSelectsArchivedWhenCurrentGone() throws Exception {
        setUpMocks(true);
        stubCurrent(null);
        List<Session> list = sessions(session("arch-1", true), session("arch-2", true));
        when(sessionControl.getCustomTitle(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));

        SessionLifecycleHandler handler = newHandler();
        handler.onSessionListUpdated(list);
        flushEdt();

        assertEquals(2, sessionDropdown.getItemCount());
        assertNotEquals(-1, sessionDropdown.getSelectedIndex(),
                "Fallback must select an archived session instead of blanking the dropdown");
        verify(sessionControl).loadSession(anyString());
    }

    @Test
    void hideArchivedFallsBackToVisibleSession() throws Exception {
        setUpMocks(false);
        stubCurrent("arch-1");
        List<Session> list = sessions(session("arch-1", true), session("vis-1", false));
        when(sessionControl.getCurrentSessionDirectory()).thenReturn("/tmp");
        when(sessionControl.getCustomTitle(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));

        SessionLifecycleHandler handler = newHandler();
        handler.onSessionListUpdated(list);
        flushEdt();

        assertEquals(1, sessionDropdown.getItemCount(), "Hidden sessions must be filtered out");
        assertNotNull(sessionDropdown.getSelectedItem());
        assertEquals("vis-1",
                ((SessionItem) sessionDropdown.getSelectedItem()).getSession().id(),
                "Hidden current session must fall back to a visible session");
        verify(sessionControl).loadSession("vis-1");
    }

    @Test
    void hideArchivedAllHiddenShowsWelcomeScreen() throws Exception {
        setUpMocks(false);
        stubCurrent("arch-1");
        List<Session> list = sessions(session("arch-1", true));

        SessionLifecycleHandler handler = newHandler();
        handler.onSessionListUpdated(list);
        flushEdt();

        assertEquals(0, sessionDropdown.getItemCount());
        assertEquals(-1, sessionDropdown.getSelectedIndex());
        verify(chatPanel).setSessionList(any(), any(), any());
    }

    @Test
    void internalPromptDoneDoesNotEndTurnWhileUserPromptInFlight() {
        setUpMocks(false);
        SessionLifecycleHandler handler = newHandler();
        handler.onUserPromptSent();
        assertEquals(false, handler.isTurnEnded());

        handler.onInternalMessageDone();
        assertEquals(false, handler.isTurnEnded(),
                "Cursor cancels overlapping prompts; preamble done must not Ready the input");
    }

    @Test
    void internalPromptDoneEndsTurnWhenNoUserPrompt() {
        setUpMocks(false);
        SessionLifecycleHandler handler = newHandler();
        handler.onInternalMessageSent();
        handler.onInternalMessageDone();
        assertEquals(true, handler.isTurnEnded());
    }

    /** Builds the wire-faithful {@code SessionUpdate} shape the decoder emits for a type. */
    private SessionUpdate updateOf(MessageType type, String sessionId) {
        return SessionUpdate.syntheticTurnEnd(sessionId, type);
    }

    @Test
    void availableCommandsUpdateDoesNotEndTurnWhileUserPromptInFlight() throws Exception {
        // The documented goose wedge: available_commands_update arrives at turn START;
        // flipping turnEnded here lets the next prompt bypass the queue guard, hits goose
        // with a second session/prompt while one is in flight, and wedges the session.
        setUpMocks(false);
        SessionLifecycleHandler handler = newHandler();
        handler.onUserPromptSent();

        handler.onSessionUpdate(updateOf(MessageType.available_commands_update, "goose-sess-1"));
        flushEdt();

        assertEquals(false, handler.isTurnEnded(),
            "available_commands_update is turn-START, never turn-end");
    }

    @Test
    void syntheticRespondingFinishedEndsTurnAndFiresCallback() throws Exception {
        setUpMocks(false);
        SessionLifecycleHandler handler = newHandler();
        java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
        handler.setOnTurnEndedCallback(() -> {
            callbacks.incrementAndGet();
            return true;
        });
        handler.onUserPromptSent();
        assertEquals(false, handler.isTurnEnded());

        // Synthetic textual turn-end, exactly as SessionUpdateDecoder produces it.
        handler.onSessionUpdate(updateOf(MessageType.responding_finished, "goose-sess-1"));
        flushEdt();

        assertEquals(true, handler.isTurnEnded(), "responding_finished is the authoritative SSE turn-end");
        assertEquals(1, callbacks.get(), "turn-end callback must fire once");
    }

}
