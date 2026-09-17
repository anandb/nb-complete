package github.anandb.netbeans.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionConfigSelectOption;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProcessService;
import github.anandb.netbeans.ui.platform.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openide.util.NbBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The mode combo's label and tooltip must name the field its values were read
 * from: "Mode" for harnesses that advertise modes via the session/new `modes`
 * field (the {@code supportsSessionSetMode} harnesses), "Agent" for those whose
 * modes come from {@code configOptions}. Headless: no IDE window is opened.
 *
 * <p>Mockito static mocks are thread-local while {@code updateConfigControls}
 * applies its work on the EDT, so the mocks, the controller, and the read-back
 * all happen on the EDT; the values are read in a second EDT task, after the
 * update task that {@code invokeLater} queued has drained.
 */
class ConfigPanelControllerModeLabelTest {

    private static final List<SessionConfigOption> MODE_OPTION = List.of(
            new SessionConfigOption("mode", "Mode", "Session permission mode", "mode", "select", "default",
                    List.of(new SessionConfigSelectOption("default", "Manual", "Always ask before changes"))));

    /** The rendered label pair, snapshot from the EDT. */
    private record Rendered(String text, String tooltip) { }

    private final AtomicReference<ConfigPanelController> controller = new AtomicReference<>();
    private MockedStatic<PlatformBridge> platformBridgeMock;
    private MockedStatic<ThemeManager> themeManagerMock;
    private MockedStatic<PluginSettings> pluginSettingsMock;

    /** Applies a mode config option for {@code harness} and returns the rendered label pair. */
    private Rendered renderFor(HarnessCatalog.Harness harness) throws Exception {
        AtomicReference<Rendered> rendered = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            platformBridgeMock = Mockito.mockStatic(PlatformBridge.class);
            themeManagerMock = Mockito.mockStatic(ThemeManager.class);
            pluginSettingsMock = Mockito.mockStatic(PluginSettings.class);

            ProcessControl processControl = mock(ProcessControl.class);
            when(processControl.getCapabilities()).thenReturn(harness);
            platformBridgeMock.when(PlatformBridge::processServiceSafe)
                    .thenReturn((ProcessService) () -> processControl);
            platformBridgeMock.when(PlatformBridge::sessionServiceSafe)
                    .thenReturn((SessionService) () -> mock(SessionControl.class));
            themeManagerMock.when(() -> ThemeManager.getIcon(anyString(), anyInt())).thenReturn(null);
            pluginSettingsMock.when(PluginSettings::getToolbarIconSize).thenReturn(16);

            controller.set(new ConfigPanelController(tabName -> { }));
            controller.get().updateConfigControls(MODE_OPTION);
        });

        // Runs after the update task above has been drained from the EDT queue.
        SwingUtilities.invokeAndWait(() -> {
            JLabel label = controller.get().getModeLabel();
            rendered.set(new Rendered(label.getText(), label.getToolTipText()));
            closeStaticMocks();
        });

        return rendered.get();
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

    /** The bundle text for {@code key}, so assertions track the key rather than its copy. */
    private static String message(String key) {
        return NbBundle.getMessage(ConfigPanelController.class, key);
    }

    @AfterEach
    void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(this::closeStaticMocks);
    }

    @Test
    void labelsModeWhenHarnessAdvertisesTheModesField() throws Exception {
        // Claude and Oh My Pi carry both fields; Gemini has no configOption
        // fallback at all, so the modes field is its only source.
        for (HarnessCatalog.Harness harness : List.of(HarnessCatalog.CLAUDE, HarnessCatalog.GEMINI,
                HarnessCatalog.OMP)) {
            Rendered rendered = renderFor(harness);
            assertEquals(message("LBL_Mode"), rendered.text(), "label for harness " + harness.id());
            assertEquals(message("HINT_Mode"), rendered.tooltip(), "tooltip for harness " + harness.id());
        }
    }

    @Test
    void labelsAgentWhenModesComeFromConfigOptions() throws Exception {
        for (HarnessCatalog.Harness harness : List.of(HarnessCatalog.OPENCODE, HarnessCatalog.CURSOR,
                HarnessCatalog.HERMES)) {
            Rendered rendered = renderFor(harness);
            assertEquals(message("LBL_Agent"), rendered.text(), "label for harness " + harness.id());
            assertEquals(message("HINT_Agent"), rendered.tooltip(), "tooltip for harness " + harness.id());
        }
    }

    @Test
    void labelAndTooltipMoveTogether() throws Exception {
        Rendered modesField = renderFor(HarnessCatalog.CLAUDE);
        Rendered configOptions = renderFor(HarnessCatalog.OPENCODE);

        assertNotEquals(modesField.text(), configOptions.text());
        assertNotEquals(modesField.tooltip(), configOptions.tooltip(),
                "the tooltip must describe the field the label names");
    }
}
