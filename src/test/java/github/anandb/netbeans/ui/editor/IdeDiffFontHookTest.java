package github.anandb.netbeans.ui.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.support.PluginSettings;

/**
 * The hook reaches into another module's component tree, so it must obey the Options switch that
 * gates it instead of running unconditionally.
 */
class IdeDiffFontHookTest {

    private boolean original;

    @BeforeEach
    void rememberSetting() {
        original = PluginSettings.isDiffIdeFontEnabled();
    }

    @AfterEach
    void restoreSetting() {
        PluginSettings.setDiffIdeFontEnabled(original);
    }

    @Test
    void disabledWhenTheSettingIsOff() {
        PluginSettings.setDiffIdeFontEnabled(false);
        assertFalse(IdeDiffFontHook.isEnabled());
    }

    @Test
    void enabledWhenTheSettingIsOn() {
        PluginSettings.setDiffIdeFontEnabled(true);
        assertTrue(IdeDiffFontHook.isEnabled());
    }
}
