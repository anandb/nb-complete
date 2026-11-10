package github.anandb.netbeans.ui.chat;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import java.awt.Component;
import java.awt.Container;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import github.anandb.netbeans.ui.TestUiUtils;

/**
 * A turn-end can arrive between message-drain batches. Finalizing at that point used to leave
 * the tail chunk to create a fresh streaming bubble afterwards, which stayed a plain
 * {@code JTextArea} until the next turn. Finalization now waits for the queue to drain.
 */
class ChatThreadPanelStreamingFinalizeTest {

    private ChatThreadPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        SwingUtilities.invokeAndWait(() -> {
            panel = new ChatThreadPanel();
            // id-strict: the late chunk must open its own bubble, not merge into the first.
            panel.setIgnoresMessageIdForStreamingForTest(Boolean.FALSE);
        });
    }

    @Test
    void stopBetweenDrainBatchesFinalizesTheQueuedTailChunk() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.addMessage(new ProcessedMessage.Builder()
                    .messageType(MessageType.agent_message_chunk)
                    .text("**tail** chunk")
                    .messageId("m1")
                    .kind("agent")
                    .rawText("**tail** chunk")
                    .streaming(true)
                    .build());
            // The chunk is queued; the drain has not run yet. A turn-end here must not
            // finalize before it, or the chunk opens a bubble that is never converted.
            panel.stopStreaming();
        });
        flushEdt();

        MessageBubble assistant = findBubble("assistant");
        assertNotNull(assistant, "the queued tail chunk was not rendered");
        assertFalse(assistant.hasStreamingTextArea(),
                "the last bubble stayed a streaming JTextArea");
        assertTrue(containsHtmlPane(assistant),
                "the finalized bubble has no FitEditorPane");
    }

    @Test
    void chunkArrivingAfterTurnEndStillFinalizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.addMessage(streaming("first", "m1")));
        flushEdt();
        SwingUtilities.invokeAndWait(() -> panel.stopStreaming());

        // The turn is over, the queue is empty, and now a late chunk opens a fresh bubble.
        SwingUtilities.invokeAndWait(() -> panel.addMessage(streaming("late **tail**", "m2")));
        Thread.sleep(200);
        flushEdt();

        MessageBubble late = findBubble("assistant");
        assertNotNull(late, "the late chunk was not rendered");
        assertFalse(late.hasStreamingTextArea(),
                "a chunk arriving after turn-end left a streaming JTextArea");
        assertTrue(containsHtmlPane(late),
                "the late bubble has no FitEditorPane");
    }

    private ProcessedMessage streaming(String text, String messageId) {
        return new ProcessedMessage.Builder()
                .messageType(MessageType.agent_message_chunk)
                .text(text)
                .messageId(messageId)
                .kind("agent")
                .rawText(text)
                .streaming(true)
                .build();
    }

    private void flushEdt() throws Exception {
        // Cover the 200 ms streaming-bubble cooldown.
        for (int i = 0; i < 6; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(40);
        }
    }

    private MessageBubble findBubble(String role) throws Exception {
        MessageBubble[] found = new MessageBubble[1];
        SwingUtilities.invokeAndWait(() -> {
            for (Component c : panel.getMessagesContainer().getComponents()) {
                if (c instanceof MessageBubble mb && role.equals(mb.getRole())) {
                    found[0] = mb;
                }
            }
        });
        return found[0];
    }

    private static boolean containsHtmlPane(Component c) {
        if (c.getClass().getSimpleName().equals("FitEditorPane")) {
            return true;
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (containsHtmlPane(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
