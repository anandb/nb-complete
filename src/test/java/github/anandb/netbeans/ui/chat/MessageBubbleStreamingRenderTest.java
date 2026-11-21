package github.anandb.netbeans.ui.chat;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.TimeUnit;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.ui.TestUiUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A streamed turn must stay a lightweight {@code JTextArea} and pay HTML
 * rendering exactly once, at finalization. The mini assistant and the main
 * thread both build bubbles through {@link MessageBubble}, so this covers the
 * shared path the review flagged: per-chunk {@code FitEditorPane.setText()}.
 */
class MessageBubbleStreamingRenderTest {

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
    }

    @Test
    void streamedChunksNeverRenderHtml() throws Exception {
        MessageBubble[] bubble = new MessageBubble[1];
        SwingUtilities.invokeAndWait(() -> bubble[0] = new MessageBubble(
                MessageType.agent_message_chunk, "first ", "m1", null,
                MessageBubble.AvatarPosition.NONE, true, null));
        try {
            assertTrue(bubble[0].hasStreamingTextArea(),
                    "a streaming bubble must start as a JTextArea");

            for (int i = 0; i < 40; i++) {
                SwingUtilities.invokeAndWait(() -> {
                    bubble[0].appendText("chunk ");
                    bubble[0].flushUpdate(true);
                });
            }

            assertEquals(0, countHtmlPanes(bubble[0]),
                    "a streamed chunk rendered HTML mid-turn");
            assertTrue(bubble[0].hasStreamingTextArea(),
                    "streaming text lost its JTextArea");
            assertNull(findHtmlPane(bubble[0]));
        } finally {
            SwingUtilities.invokeAndWait(() -> bubble[0].removeNotify());
        }
    }

    @Test
    void finalizationRendersHtmlExactlyOnce() throws Exception {
        MessageBubble[] bubble = new MessageBubble[1];
        SwingUtilities.invokeAndWait(() -> bubble[0] = new MessageBubble(
                MessageType.agent_message_chunk, "first ", "m1", null,
                MessageBubble.AvatarPosition.NONE, true, null));
        try {
            SwingUtilities.invokeAndWait(() -> {
                bubble[0].appendText("second");
                bubble[0].flushUpdate(true);
            });
            SwingUtilities.invokeAndWait(() -> bubble[0].finalizeStreaming(false));
            flushEdt();

            assertEquals(1, countHtmlPanes(bubble[0]),
                    "finalization must leave exactly one HTML pane");
            assertFalse(bubble[0].hasStreamingTextArea(),
                    "the JTextArea survived finalization");
            String rendered = htmlText(findHtmlPane(bubble[0]));
            assertTrue(rendered.contains("first") && rendered.contains("second"),
                    () -> "the HTML pane lost streamed text: " + rendered);
        } finally {
            SwingUtilities.invokeAndWait(() -> bubble[0].removeNotify());
        }
    }

    /** Text collected from the single HTML pane's document. */
    private static String htmlText(Component htmlPane) throws Exception {
        String[] text = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            if (htmlPane instanceof JTextPane pane) {
                try {
                    text[0] = pane.getDocument().getText(0, pane.getDocument().getLength());
                } catch (javax.swing.text.BadLocationException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        });
        return text[0] == null ? "" : text[0];
    }

    private static void flushEdt() throws Exception {
        for (int i = 0; i < 6; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            TimeUnit.MILLISECONDS.sleep(40);
        }
    }

    private static Component findHtmlPane(Component c) {
        if (c.getClass().getSimpleName().equals("FitEditorPane")) {
            return c;
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component hit = findHtmlPane(child);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static int countHtmlPanes(Component c) {
        int total = c.getClass().getSimpleName().equals("FitEditorPane") ? 1 : 0;
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                total += countHtmlPanes(child);
            }
        }
        return total;
    }
}
