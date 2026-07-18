package github.anandb.netbeans.support;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutUtilsTest {

    @Test
    void testFormatKeyStroke_simple() {
        KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);
        String formatted = ShortcutUtils.formatKeyStroke(ks);
        assertEquals("Ctrl + A", formatted);
    }

    @Test
    void testFormatKeyStroke_null() {
        String formatted = ShortcutUtils.formatKeyStroke(null);
        assertEquals("", formatted);
    }

    @Test
    void testFormatKeyStroke_complex() {
        KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK);
        String formatted = ShortcutUtils.formatKeyStroke(ks);
        assertTrue(formatted.contains("Ctrl"));
        assertTrue(formatted.contains("Shift"));
        assertTrue(formatted.contains("L"));
    }

    @Test
    void testResolveShortcut_nonexistent() {
        String res = ShortcutUtils.resolveShortcut("invalid.action.id.for.testing");
        assertEquals("", res);
    }
}
