package github.anandb.netbeans.ui.chat;

import github.anandb.netbeans.model.MessageType;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import github.anandb.netbeans.ui.TestUiUtils;

/**
 * Removed bubbles must not be retained by the wheel-redirect map: every
 * removal path (trim, blank sweep, echo drop, self-removal) goes through
 * the container listener, which unfixes on remove.
 */
class ChatThreadPanelWheelLeakTest {

    private ChatThreadPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        SwingUtilities.invokeAndWait(() -> panel = new ChatThreadPanel());
    }

    @Test
    void removingBubbleReleasesWheelHandle() throws Exception {
        MessageBubble[] bubble = new MessageBubble[1];
        SwingUtilities.invokeAndWait(() -> {
            bubble[0] = new MessageBubble(MessageType.tool_call, "out", null,
                    "Search", MessageBubble.AvatarPosition.NONE);
            panel.getMessagesContainer().add(bubble[0]);
        });
        assertEquals(1, bubble[0].getMouseWheelListeners().length);

        SwingUtilities.invokeAndWait(() -> panel.getMessagesContainer().remove(bubble[0]));

        assertEquals(0, bubble[0].getMouseWheelListeners().length);
    }

    @Test
    void readdedBubbleStacksNoDuplicateWheelFix() throws Exception {
        MessageBubble[] bubble = new MessageBubble[1];
        SwingUtilities.invokeAndWait(() -> {
            bubble[0] = new MessageBubble(MessageType.tool_call, "out", null,
                    "Search", MessageBubble.AvatarPosition.NONE);
            panel.getMessagesContainer().add(bubble[0]);
            panel.getMessagesContainer().remove(bubble[0]);
            panel.getMessagesContainer().add(bubble[0]);
        });

        assertEquals(1, bubble[0].getMouseWheelListeners().length);
    }
}
