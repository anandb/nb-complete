package github.anandb.netbeans.ui;

import github.anandb.netbeans.support.PreferenceKeys;
import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MiniAssistantDialogTest {

    @Test
    void testMiniAssistantGeometryPreferenceKeysExist() {
        assertEquals("miniAssistant.x", PreferenceKeys.MINI_ASSISTANT_X);
        assertEquals("miniAssistant.y", PreferenceKeys.MINI_ASSISTANT_Y);
        assertEquals("miniAssistant.width", PreferenceKeys.MINI_ASSISTANT_WIDTH);
        assertEquals("miniAssistant.height", PreferenceKeys.MINI_ASSISTANT_HEIGHT);
        assertEquals("miniAssistant.inputHeight", PreferenceKeys.MINI_ASSISTANT_INPUT_HEIGHT);
    }

    @Test
    void testMiniAssistantBoundsPersistence() {
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        prefs.putInt(PreferenceKeys.MINI_ASSISTANT_WIDTH, 600);
        prefs.putInt(PreferenceKeys.MINI_ASSISTANT_HEIGHT, 400);
        prefs.putInt(PreferenceKeys.MINI_ASSISTANT_INPUT_HEIGHT, 120);

        assertEquals(600, prefs.getInt(PreferenceKeys.MINI_ASSISTANT_WIDTH, 500));
        assertEquals(400, prefs.getInt(PreferenceKeys.MINI_ASSISTANT_HEIGHT, 300));
        assertEquals(120, prefs.getInt(PreferenceKeys.MINI_ASSISTANT_INPUT_HEIGHT, -1));
    }

    @Test
    void testCloseIfVisibleDoesNotThrowWhenNull() {
        Assertions.assertDoesNotThrow(MiniAssistantDialog::closeIfVisible);
    }
}
