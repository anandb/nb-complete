package github.anandb.netbeans.ui.session;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuListener;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.model.ModelRecords.ConfigItem;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionConfigSelectOption;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProcessService;
import github.anandb.netbeans.ui.platform.SessionService;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The thinking/effort option's id is harness-specific (OpenCode "effort", goose
 * "thinking_effort"). The combo's listeners are installed once, on the first session that
 * declares an option, and the panel outlives a harness switch — so the id captured at
 * install time must never be used for a later send, or the server answers
 * {@code Invalid params: Unsupported config option: effort}. An option that declares no id
 * (or a session that has not reported its options yet) must send nothing rather than a guess.
 *
 * <p>Headless: the combo's popup listeners are invoked directly instead of showing a popup.
 */
class ConfigPanelControllerThinkingIdTest {

    private final AtomicReference<ConfigPanelController> controller = new AtomicReference<>();
    private final AtomicReference<SessionControl> sessionRef = new AtomicReference<>();

    private MockedStatic<PlatformBridge> platformBridgeMock;
    private MockedStatic<ThemeManager> themeManagerMock;
    private MockedStatic<PluginSettings> pluginSettingsMock;

    @Test
    void sendsTheCurrentSessionsThinkingOptionIdNotTheOneCapturedAtListenerInstall() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ConfigPanelController panel = newPanel();
            controller.set(panel);
            // First session declares "effort" (OpenCode-style id).
            panel.updateConfigControls(thinkingOptions("effort"));
            // A later update declares the goose id; the listener must not keep sending "effort".
            panel.updateConfigControls(thinkingOptions("thinking_effort"));
        });

        // Runs after both queued updateConfigControls tasks have drained.
        SwingUtilities.invokeAndWait(() -> {
            try {
                JComboBox<ConfigItem> thinking = controller.get().getThinkingCombo();
                int medium = indexOfValue(thinking, "medium");
                assertTrue(medium >= 0, "the server-declared levels must populate the combo");

                selectThroughPopup(thinking, medium);
                verify(sessionRef.get()).setSessionConfigOption("s1", "thinking_effort", "medium");
            } finally {
                closeStaticMocks();
            }
        });
    }

    @Test
    void aThinkingOptionWithoutAnIdSendsNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ConfigPanelController panel = newPanel();
            controller.set(panel);
            // A harness may report the effort option without an id. Guess "effort" is exactly
            // what goose rejects, so no request may be sent at all.
            panel.updateConfigControls(thinkingOptions(null));
        });

        SwingUtilities.invokeAndWait(() -> {
            try {
                JComboBox<ConfigItem> thinking = controller.get().getThinkingCombo();
                int medium = indexOfValue(thinking, "medium");
                assertTrue(medium >= 0, "the levels are still listed");

                selectThroughPopup(thinking, medium);
                verify(sessionRef.get(), never()).setSessionConfigOption(anyString(), anyString(), anyString());
            } finally {
                closeStaticMocks();
            }
        });
    }

    @Test
    void clearSessionConfigDropsThePreviousSessionsLevelsAndId() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ConfigPanelController panel = newPanel();
            controller.set(panel);
            panel.updateConfigControls(thinkingOptions("effort"));
        });

        SwingUtilities.invokeAndWait(() -> {
            try {
                ConfigPanelController panel = controller.get();
                JComboBox<ConfigItem> thinking = panel.getThinkingCombo();
                assertTrue(thinking.getItemCount() > 0, "the first session's levels are listed");

                // A session load starts: the previous harness's option must not survive it,
                // or a selection made before the new options arrive is sent with its id.
                panel.clearSessionConfig();

                assertEquals(0, thinking.getItemCount(),
                        "the previous harness's levels must not stay selectable");
                assertTrue(!thinking.isEnabled(), "an empty thinking combo is disabled");
                for (PopupMenuListener listener : thinking.getPopupMenuListeners()) {
                    listener.popupMenuWillBecomeInvisible(null);
                }
                verify(sessionRef.get(), never()).setSessionConfigOption(anyString(), anyString(), anyString());
            } finally {
                closeStaticMocks();
            }
        });
    }

    /** Installs the static mocks, the session double and the controller under test. Runs on the EDT. */
    private ConfigPanelController newPanel() {
        platformBridgeMock = Mockito.mockStatic(PlatformBridge.class);
        themeManagerMock = Mockito.mockStatic(ThemeManager.class);
        pluginSettingsMock = Mockito.mockStatic(PluginSettings.class);

        SessionControl session = mock(SessionControl.class);
        when(session.getCurrentSessionId()).thenReturn("s1");
        when(session.setSessionConfigOption(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        sessionRef.set(session);

        ProcessControl processControl = mock(ProcessControl.class);
        when(processControl.getCapabilities()).thenReturn(HarnessCatalog.GOOSE);
        platformBridgeMock.when(PlatformBridge::processServiceSafe)
                .thenReturn((ProcessService) () -> processControl);
        platformBridgeMock.when(PlatformBridge::sessionServiceSafe)
                .thenReturn((SessionService) () -> session);
        platformBridgeMock.when(PlatformBridge::prefStoreSafe).thenCallRealMethod();
        platformBridgeMock.when(PlatformBridge::sessionControl).thenCallRealMethod();
        platformBridgeMock.when(PlatformBridge::processControl).thenCallRealMethod();
        themeManagerMock.when(() -> ThemeManager.getIcon(anyString(), anyInt())).thenReturn(null);
        pluginSettingsMock.when(PluginSettings::getToolbarIconSize).thenReturn(16);

        return new ConfigPanelController(tabName -> { });
    }

    /** Simulates the user picking {@code index} in the combo's popup. */
    private static void selectThroughPopup(JComboBox<ConfigItem> combo, int index) {
        for (PopupMenuListener listener : combo.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeVisible(null);
        }
        combo.setSelectedIndex(index);
        for (PopupMenuListener listener : combo.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeInvisible(null);
        }
    }

    private static int indexOfValue(JComboBox<ConfigItem> combo, String value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (value.equals(combo.getItemAt(i).value())) {
                return i;
            }
        }
        return -1;
    }

    private static List<SessionConfigOption> thinkingOptions(String id) {
        return List.of(new SessionConfigOption(id, "Thinking effort", null, "thought_level", "select", "low",
                List.of(new SessionConfigSelectOption("off", "off", null),
                        new SessionConfigSelectOption("low", "low", null),
                        new SessionConfigSelectOption("medium", "medium", null),
                        new SessionConfigSelectOption("high", "high", null))));
    }

    private void closeStaticMocks() {
        if (platformBridgeMock != null) {
            platformBridgeMock.close();
            platformBridgeMock = null;
        }
        if (themeManagerMock != null) {
            themeManagerMock.close();
            themeManagerMock = null;
        }
        if (pluginSettingsMock != null) {
            pluginSettingsMock.close();
            pluginSettingsMock = null;
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(this::closeStaticMocks);
    }
}
