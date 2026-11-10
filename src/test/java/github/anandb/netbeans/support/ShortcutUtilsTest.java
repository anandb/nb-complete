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
        KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
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

    @Test
    void parsesNetBeansShortcutNamesAndModifiers() {
        boolean mac = org.openide.util.Utilities.isMac();
        String modifier = mac ? "Cmd + Option + Shift + " : "Ctrl + Alt + Shift + ";
        assertEquals(modifier + "J", ShortcutUtils.parseShortcutFilename("DAS-J"));
        assertEquals("", ShortcutUtils.parseShortcutFilename("plain"));
        assertEquals("", ShortcutUtils.parseShortcutFilename("D-"));
        assertEquals("Q", ShortcutUtils.parseShortcutFilename("X-Q"));
    }

    @Test
    void formatsAllNetBeansSpecialKeyNames() {
        boolean mac = org.openide.util.Utilities.isMac();
        String[][] keys = {
            {"DELETE", "Delete"}, {"INSERT", "Insert"}, {"ENTER", "Enter"}, {"PAGE_UP", "Page Up"},
            {"PAGE_DOWN", "Page Down"}, {"HOME", "Home"}, {"END", "End"}, {"ESCAPE", "Escape"},
            {"TAB", "Tab"}, {"SPACE", "Space"}, {"BACK_SPACE", "Backspace"}, {"UP", "↑"},
            {"DOWN", "↓"}, {"LEFT", "←"}, {"RIGHT", "→"}, {"F12", "F12"}, {"X", "X"},
            {"unknown", "Unknown"}
        };
        for (String[] key : keys) {
            assertEquals(key[1], ShortcutUtils.formatNetBeansKeyName(key[0]), key[0]);
        }
        String allModifiers = mac ? "Ctrl + Cmd + Option + Shift + A" : "Ctrl + Meta + Alt + Shift + A";
        assertEquals(allModifiers, ShortcutUtils.formatKeyStroke(
                KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK
                        | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
        assertEquals("x", ShortcutUtils.formatKeyStroke(KeyStroke.getKeyStroke('x')));
    }
}
