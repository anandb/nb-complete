package github.anandb.netbeans.ui.chat;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.TestUiUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The thread's wheel step must come from the Chat Behavior preference. */
class ChatThreadPanelScrollSpeedTest {

    @BeforeEach
    void setUp() {
        TestUiUtils.setupTestUIManager();
    }

    @Test
    void appliesTheConfiguredScrollSpeed() throws Exception {
        int original = PluginSettings.getChatScrollSpeed();
        try {
            PluginSettings.setChatScrollSpeed(32);
            ChatThreadPanel[] panel = new ChatThreadPanel[1];
            SwingUtilities.invokeAndWait(() -> panel[0] = new ChatThreadPanel());

            JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class, panel[0].getMessagesContainer());
            assertNotNull(scrollPane, "chat thread must live in a scroll pane");
            assertEquals(32, scrollPane.getVerticalScrollBar().getUnitIncrement());
        } finally {
            PluginSettings.setChatScrollSpeed(original);
        }
    }
}
