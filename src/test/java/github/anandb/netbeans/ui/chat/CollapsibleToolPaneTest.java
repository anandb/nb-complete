package github.anandb.netbeans.ui.chat;

import org.junit.jupiter.api.Test;

import javax.swing.UIManager;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import github.anandb.netbeans.support.PluginSettings;

class CollapsibleToolPaneTest {

    @Test
    void testToolPane() {
        UIManager.put("Panel.background", Color.BLACK);
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", Color.YELLOW);

        CollapsibleToolPane pane = new CollapsibleToolPane("TOOL: run_command", "ls -la", true);
        assertEquals("run_command", pane.headerLabel.getText());
        assertTrue(pane.isExpanded());
    }

    @Test
    void testThinkingPane() {
        UIManager.put("Panel.background", Color.BLACK);
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", Color.YELLOW);

        CollapsibleToolPane pane = new CollapsibleToolPane("THINKING", "Searching...", true);
        assertEquals("Thinking", pane.headerLabel.getText());
    }

    @Test
    void parameterToggleSurvivesReparenting() throws Exception {
        // Adopting a pane into a composite bubble removes and re-adds it, which
        // fires removeNotify/addNotify. The parameter must still toggle.
        CollapsibleToolPane pane = new CollapsibleToolPane("edit Foo.java", false);
        javax.swing.JPanel host = new javax.swing.JPanel(null);
        host.setSize(600, 200);
        host.add(pane);
        assertNotNull(pane.paramLabel, "a titled parameter creates the label");
        int before = pane.paramLabel.getMouseListeners().length;

        javax.swing.SwingUtilities.invokeAndWait(() -> {
            pane.removeNotify();
            pane.addNotify();
        });

        assertEquals(before, pane.paramLabel.getMouseListeners().length,
                "reparenting must re-arm the parameter toggle exactly once");
        boolean expanded = pane.isExpanded();
        javax.swing.SwingUtilities.invokeAndWait(() -> pane.paramLabel.dispatchEvent(
                new java.awt.event.MouseEvent(pane.paramLabel,
                        java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                        0, 1, 1, 1, false)));
        assertEquals(!expanded, pane.isExpanded(), "clicking the parameter still toggles");
    }

    @Test
    void headerShowsSaveBeforeCopyAndDisabledWithoutDirectory() {
        String previous = PluginSettings.getMessageSaveDirectory();
        try {
            PluginSettings.setMessageSaveDirectory("");
            CollapsibleToolPane pane = new CollapsibleToolPane("TOOL: run_command", "ls", true);
            assertNotNull(pane.saveButton, "tool pane header offers save");
            assertFalse(pane.saveButton.isEnabled(), "save is disabled until a directory is set");
            assertTrue(pane.saveButton.getToolTipText().contains("Settings -> Core Service -> Assistant"),
                    "tooltip names the setting: " + pane.saveButton.getToolTipText());
            java.awt.Container row = pane.saveButton.getParent();
            org.junit.jupiter.api.Assertions.assertSame(pane.saveButton, row.getComponent(0),
                    "save comes first");
            org.junit.jupiter.api.Assertions.assertSame(pane.copyButton, row.getComponent(1),
                    "copy follows save");
        } finally {
            PluginSettings.setMessageSaveDirectory(previous);
        }
    }

    @Test
    void activityPaneHeaderOffersSave() {
        String previous = PluginSettings.getMessageSaveDirectory();
        try {
            PluginSettings.setMessageSaveDirectory("");
            CollapsibleActivityPane pane = new CollapsibleActivityPane("THINKING", "step", true);
            assertNotNull(pane.saveButton, "thought/activity pane header offers save");
        } finally {
            PluginSettings.setMessageSaveDirectory(previous);
        }
    }

    @Test
    void codePaneHeaderKeepsCopyOnly() {
        CollapsibleCodePane pane = new CollapsibleCodePane("java", "int x = 1;", true);
        org.junit.jupiter.api.Assertions.assertNull(pane.saveButton, "code panes keep copy only");
    }
}
