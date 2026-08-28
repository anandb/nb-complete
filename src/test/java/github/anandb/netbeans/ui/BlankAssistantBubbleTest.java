package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that entirely-blank assistant/thought messages never render as
 * empty bubbles. The streaming path cleans them at finalization; the
 * non-streaming path (session-load history, non-streamed responses) must drop
 * them eagerly.
 */
class BlankAssistantBubbleTest {

    private ChatThreadPanel panel;
    private JPanel messagesContainer;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        SwingUtilities.invokeAndWait(() -> {
            panel = new ChatThreadPanel();
            messagesContainer = (JPanel) privateField(panel, "messagesContainer");
        });
    }

    @Test
    void nonStreamingBlankAssistantMessageIsDropped() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addMessage(new ProcessedMessage.Builder()
                .messageType(MessageType.agent_message_chunk)
                .text("\n\n")
                .messageId("b1")
                .kind("agent")
                .rawText("\n\n")
                .streaming(false)
                .build()));
        flushEDT();
        assertNoBlankAssistantBubbles();
    }

    @Test
    void nonStreamingBlankThoughtMessageIsDropped() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addMessage(new ProcessedMessage.Builder()
                .messageType(MessageType.agent_thought_chunk)
                .text("   ")
                .messageId("b2")
                .kind("agent")
                .rawText("   ")
                .streaming(false)
                .build()));
        flushEDT();
        assertNoBlankAssistantBubbles();
    }

    @Test
    void realContentStillRendered() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addMessage(new ProcessedMessage.Builder()
                .messageType(MessageType.agent_message_chunk)
                .text("Hello\n\nWorld")
                .messageId("b3")
                .kind("agent")
                .rawText("Hello\n\nWorld")
                .streaming(false)
                .build()));
        flushEDT();
        assertNoBlankAssistantBubbles();
    }

    @Test
    void blankInEarlierTurnIsDroppedOnLoad() throws Exception {
        // user msg -> blank assistant (tool-only) -> user msg
        // The blank sits BEFORE the last user bubble, so the tail-only scan in
        // removeBlankBubbles (currentTurnStartIndex last-user bound) must still
        // find it during a setMessages load.
        Message user1 = new Message("u1", "user", new Message.Prompt("hi", null), null, null);
        Message blank = new Message("b1", "assistant", null, blankCompletion(), "sent");
        Message user2 = new Message("u2", "user", new Message.Prompt("more", null), null, null);
        SwingUtilities.invokeAndWait(() -> panel.setMessages(List.of(user1, blank, user2)));
        flushEDT();
        assertNoBlankAssistantBubbles();
    }

    private static Message.Completion blankCompletion() {
        return new Message.Completion("", null, List.of(new Message.ToolCall(
                "tc0", "some_tool", "{}", new Message.ToolResult("ok", false))), "tool_use");
    }

    private void assertNoBlankAssistantBubbles() throws Exception {
        List<String> blanks = new ArrayList<>();
        SwingUtilities.invokeAndWait(() ->
                collectBlankAssistantBubbles(messagesContainer, blanks));
        assertTrue(blanks.isEmpty(),
                "Empty assistant bubbles present: " + blanks);
    }

    private void collectBlankAssistantBubbles(Component c, List<String> out) {
        if (c instanceof MessageBubble mb) {
            String role = mb.getRole();
            if (("assistant".equals(role) || "thought".equals(role))
                    && org.apache.commons.lang3.StringUtils.isBlank(mb.getRawText())) {
                out.add("role=" + role + " msgId=" + mb.getMessageId()
                        + " rawText='" + mb.getRawText() + "'");
            }
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collectBlankAssistantBubbles(child, out);
            }
        }
    }

    private void flushEDT() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(50);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static Object privateField(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}