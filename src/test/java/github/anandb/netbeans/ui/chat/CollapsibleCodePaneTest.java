package github.anandb.netbeans.ui.chat;

import org.junit.jupiter.api.Test;

import javax.swing.UIManager;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollapsibleCodePaneTest {

    @Test
    void testLanguageMapping() {
        // Initialize UIManager for ThemeManager
        UIManager.put("Panel.background", Color.BLACK);
        UIManager.put("Label.foreground", Color.WHITE);

        CollapsibleCodePane pane = new CollapsibleCodePane("java", "public class Test {}", true);
        assertEquals("JAVA", pane.headerLabel.getText());
        assertTrue(pane.isExpanded());
    }

    @Test
    void testUpdateContent() {
        UIManager.put("Panel.background", Color.BLACK);
        UIManager.put("Label.foreground", Color.WHITE);

        CollapsibleCodePane pane = new CollapsibleCodePane("python", "print('hello')", true);
        pane.updateContent("java", "System.out.println('hi');");

        assertEquals("JAVA", pane.headerLabel.getText());
    }

    /** The block keeps a 1px inset on each side so its background never shares the bubble's edge. */
    @Test
    void codeBlockIsInsetOnePixelFromItsSides() {
        UIManager.put("Panel.background", Color.BLACK);
        UIManager.put("Label.foreground", Color.WHITE);

        CollapsibleCodePane pane = new CollapsibleCodePane("java", "int x = 1;", true);

        assertEquals(1, pane.getInsets().left);
        assertEquals(1, pane.getInsets().right);
        assertEquals(0, pane.getInsets().top);
        assertEquals(0, pane.getInsets().bottom);
    }
}
