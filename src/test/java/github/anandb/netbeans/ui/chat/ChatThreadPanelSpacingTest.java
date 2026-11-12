package github.anandb.netbeans.ui.chat;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import java.awt.Component;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import github.anandb.netbeans.ui.TestUiUtils;

/**
 * Speaker changes get a turn gap; consecutive messages from one speaker stay tight.
 */
class ChatThreadPanelSpacingTest {

    private ChatThreadPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        SwingUtilities.invokeAndWait(() -> panel = new ChatThreadPanel());
    }

    @Test
    void speakerChangeWidensTheGapAndOneSpeakerStaysTight() throws Exception {
        add(MessageType.user_message_chunk, "u1");
        add(MessageType.user_message_chunk, "u2");
        assertEquals(2, spacerAfter("u1"), "two user messages share one run");

        add(MessageType.agent_message_chunk, "a1");
        assertEquals(8, spacerAfter("u2"), "the user -> assistant change opens the gap");
    }

    private void add(MessageType type, String id) throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addMessage(new ProcessedMessage.Builder()
                .messageType(type)
                .text(id)
                .rawText(id)
                .messageId(id)
                .build()));
        SwingUtilities.invokeAndWait(() -> { });
    }

    private int spacerAfter(String messageId) {
        Component[] components = panel.getMessagesContainer().getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof MessageBubble b && messageId.equals(b.getMessageId())) {
                return components[i + 1].getMaximumSize().height;
            }
        }
        throw new AssertionError("no bubble " + messageId);
    }
}
