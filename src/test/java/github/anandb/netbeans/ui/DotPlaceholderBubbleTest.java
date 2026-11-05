package github.anandb.netbeans.ui;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A whole-message "." is an empty-message placeholder (omp emits one between
 * tool calls, sometimes sharing the following thought's messageId). It must
 * never render as its own dot bubble, while a dot that ends real text must
 * still merge into that text.
 */
class DotPlaceholderBubbleTest {

    private ChatThreadPanel panel;
    private JPanel messagesContainer;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        SwingUtilities.invokeAndWait(() -> {
            panel = new ChatThreadPanel();
            // omp advertises message ids: exercise the id-strict merge path the
            // reported wire log came from, not the test Lookup's UNKNOWN harness.
            panel.setIgnoresMessageIdForStreamingForTest(Boolean.FALSE);
            messagesContainer = (JPanel) privateField(panel, "messagesContainer");
        });
    }

    @Test
    void dotSharingThoughtMessageIdCreatesNoAssistantBubble() throws Exception {
        feed(MessageType.agent_thought_chunk, "Checking the wire log.", "m1");
        feed(MessageType.agent_message_chunk, ".", "m1");
        flushEDT();

        List<String> bubbles = bubbles();
        assertFalse(bubbles.stream().anyMatch(b -> b.startsWith("assistant")),
                "no assistant bubble expected, got " + bubbles);
        assertTrue(bubbles.stream().anyMatch(b -> b.startsWith("thought")),
                "the thought bubble must stay, got " + bubbles);
    }

    @Test
    void dotOnlyAssistantMessageWithItsOwnIdCreatesNoBubble() throws Exception {
        feed(MessageType.agent_message_chunk, "Fixing the state line.", "m1");
        feed(MessageType.agent_message_chunk, ".", "m2");
        flushEDT();

        assertEquals(List.of("assistant raw='Fixing the state line.'"), bubbles(),
                "id-strict dot must not open or join a bubble");
    }

    @Test
    void dotOnlyAssistantChunkMergesWhenHarnessIgnoresMessageIds() throws Exception {
        panel.setIgnoresMessageIdForStreamingForTest(Boolean.TRUE);
        feed(MessageType.agent_message_chunk, "Fixing the state line", "m1");
        feed(MessageType.agent_message_chunk, ".", "m2");
        flushEDT();

        assertEquals(List.of("assistant raw='Fixing the state line.'"), bubbles(),
                "a sentence-ending dot must land in the preceding bubble");
    }

    @Test
    void messageContinuesAcrossInterleavedThought() throws Exception {
        feed(MessageType.agent_message_chunk, "First half", "m1");
        feed(MessageType.agent_thought_chunk, "Thinking between halves.", "m2");
        feed(MessageType.agent_message_chunk, " second half", "m1");
        flushEDT();

        List<String> bubbles = bubbles();
        assertTrue(bubbles.stream().noneMatch(b -> b.equals("assistant raw=' second half'")),
                "second half opened its own bubble: " + bubbles);
        assertTrue(bubbles.stream().anyMatch(b -> b.equals("assistant raw='First half second half'")),
                "message did not merge across the thought block: " + bubbles);
    }

    @Test
    void sentenceEndingDotAfterInterleavedThoughtStaysWithItsMessage() throws Exception {
        feed(MessageType.agent_message_chunk, "Fixing the state line", "m1");
        feed(MessageType.agent_thought_chunk, "Now report.", "m2");
        feed(MessageType.agent_message_chunk, ".", "m1");
        flushEDT();

        List<String> bubbles = bubbles();
        assertTrue(bubbles.stream().anyMatch(b -> b.equals("assistant raw='Fixing the state line.'")),
                "sentence-ending dot lost or split: " + bubbles);
    }

    @Test
    void dotContinuingRealTextStillMerges() throws Exception {
        feed(MessageType.agent_message_chunk, "Fixing the state line", "m1");
        feed(MessageType.agent_message_chunk, ".", "m1");
        flushEDT();

        assertEquals(List.of("assistant raw='Fixing the state line.'"), bubbles());
    }

    @Test
    void dotOnlyNonStreamingMessageIsDropped() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addMessage(new ProcessedMessage.Builder()
                .messageType(MessageType.agent_message_chunk)
                .text(".")
                .messageId("m3")
                .kind("agent")
                .rawText(".")
                .streaming(false)
                .build()));
        flushEDT();

        assertTrue(bubbles().isEmpty(), "dot-only bubble present: " + bubbles());
    }

    private void feed(MessageType type, String text, String messageId) {
        SwingUtilities.invokeLater(() -> panel.addMessage(new ProcessedMessage.Builder()
                .messageType(type)
                .text(text)
                .messageId(messageId)
                .kind("agent")
                .rawText(text)
                .streaming(true)
                .build()));
    }

    private List<String> bubbles() throws Exception {
        List<String> out = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> collect(messagesContainer, out));
        return out;
    }

    private static void collect(Component c, List<String> out) {
        if (c instanceof MessageBubble mb) {
            out.add(mb.getRole() + " raw='" + mb.getRawText() + "'");
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collect(child, out);
            }
        }
    }

    private void flushEDT() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(150);
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
