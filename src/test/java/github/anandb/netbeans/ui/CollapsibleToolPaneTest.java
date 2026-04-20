package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;
import javax.swing.UIManager;
import java.awt.Color;
import static org.junit.jupiter.api.Assertions.*;

class CollapsibleToolPaneTest {

    @Test
    void testToolPane() {
        UIManager.put("Panel.background", Color.BLACK);
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", Color.YELLOW);
        
        CollapsibleToolPane pane = new CollapsibleToolPane("TOOL: run_command", "ls -la", true);
        assertEquals("Tool: run_command", pane.headerLabel.getText());
        assertTrue(pane.isExpanded());
    }

    @Test
    void testThinkingPane() {
        UIManager.put("Panel.background", Color.BLACK);
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", Color.YELLOW);

        CollapsibleToolPane pane = new CollapsibleToolPane("THINKING", "Searching...", true);
        assertEquals("Thinking Process", pane.headerLabel.getText());
    }
}
