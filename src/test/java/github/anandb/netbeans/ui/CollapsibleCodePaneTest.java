package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;
import javax.swing.UIManager;
import java.awt.Color;
import static org.junit.jupiter.api.Assertions.*;

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
}
