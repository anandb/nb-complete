package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import java.awt.Component;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A local echo whose send failed must be removable: without it, resending the restored
 * text leaves two identical user bubbles, because user bubbles never merge.
 */
class ChatThreadPanelBubbleRemovalTest {

    private ChatThreadPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        SwingUtilities.invokeAndWait(() -> panel = new ChatThreadPanel());
    }

    @Test
    void removesTheBubbleAndItsSpacer() throws Exception {
        addUserEcho("m1");
        int before = panel.getMessagesContainer().getComponentCount();
        assertTrue(before > 0, "the echo must have been added");

        SwingUtilities.invokeAndWait(() -> panel.removeMessageBubble("m1"));

        assertTrue(panel.getMessagesContainer().getComponentCount() < before,
                "the bubble and its spacer must be gone");
        for (Component c : panel.getMessagesContainer().getComponents()) {
            if (c instanceof MessageBubble b) {
                assertNotEquals("m1", b.getMessageId());
            }
        }
    }

    @Test
    void unknownMessageIdChangesNothing() throws Exception {
        addUserEcho("m2");
        int before = panel.getMessagesContainer().getComponentCount();

        SwingUtilities.invokeAndWait(() -> panel.removeMessageBubble("nope"));

        assertEquals(before, panel.getMessagesContainer().getComponentCount());
    }

    @Test
    void removingAUserEchoRollsTheUserIndexBack() throws Exception {
        addUserEcho("m3");
        assertEquals(1, panel.getUserMessageCount(), "the echo counted as a user message");

        SwingUtilities.invokeAndWait(() -> panel.removeMessageBubble("m3"));

        assertEquals(0, panel.getUserMessageCount(),
                "a dropped echo must not shift the next user message to the other avatar side");
    }

    private void addUserEcho(String messageId) throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addMessage(new ProcessedMessage.Builder()
                .messageType(MessageType.user_message_chunk)
                .text("hello")
                .rawText("hello")
                .messageId(messageId)
                .build()));
        SwingUtilities.invokeAndWait(() -> { });
    }
}
