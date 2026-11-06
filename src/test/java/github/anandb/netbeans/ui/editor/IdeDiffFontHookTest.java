package github.anandb.netbeans.ui.editor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hook reaches into another module's component tree, so it must stay inert unless the user
 * opts in via the system property.
 */
class IdeDiffFontHookTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(IdeDiffFontHook.ENABLE_PROPERTY);
    }

    @Test
    void disabledWhenThePropertyIsAbsent() {
        System.clearProperty(IdeDiffFontHook.ENABLE_PROPERTY);
        assertFalse(IdeDiffFontHook.isEnabled());
    }

    @Test
    void disabledWhenThePropertyIsFalse() {
        System.setProperty(IdeDiffFontHook.ENABLE_PROPERTY, "false");
        assertFalse(IdeDiffFontHook.isEnabled());
    }

    @Test
    void enabledOnlyWhenThePropertyIsTrue() {
        System.setProperty(IdeDiffFontHook.ENABLE_PROPERTY, "true");
        assertTrue(IdeDiffFontHook.isEnabled());
    }
}
