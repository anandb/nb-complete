package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.MessageType;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageBubbleTest {

    @Test
    void testAppendTextPreservesNewlinesForStreaming() {
        // Test the scenario where multi-line responses get concatenated incorrectly
        MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, "", null, null, MessageBubble.AvatarPosition.NONE);

        // First append - leading newline stripped, trailing newline preserved
        bubble.appendText("line1\n");
        assertEquals("line1\n", bubble.getRawText());

        // Second append - should preserve the newline and not concatenate
        bubble.appendText("line2\n");
        assertEquals("line1\nline2\n", bubble.getRawText());

        // Third append
        bubble.appendText("line3");
        assertEquals("line1\nline2\nline3", bubble.getRawText());
    }

    @Test
    void testAppendTextHandlesLeadingNewlines() {
        // Test that leading newlines are preserved exactly as they come from the stream
        MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, "", null, null, MessageBubble.AvatarPosition.NONE);

        bubble.appendText("\n\nline1\n");
        assertEquals("\n\nline1\n", bubble.getRawText());

        bubble.appendText("line2\n");
        assertEquals("\n\nline1\nline2\n", bubble.getRawText());
    }

    @Test
    void testAppendTextWithEmptyStrings() {
        MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, "", null, null, MessageBubble.AvatarPosition.NONE);

        bubble.appendText("");
        assertEquals("", bubble.getRawText());

        bubble.appendText("hello");
        assertEquals("hello", bubble.getRawText());

        bubble.appendText("");
        assertEquals("hello", bubble.getRawText());
    }

    @Test
    void testAppendTextWithOnlyNewlines() {
        MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk, "", null, null, MessageBubble.AvatarPosition.NONE);

        bubble.appendText("\n\n\n");
        assertEquals("\n\n\n", bubble.getRawText());

        bubble.appendText("hello");
        assertEquals("\n\n\nhello", bubble.getRawText());
    }

    /**
     * Regression test: ToolThoughtCombiner constructs a combined bubble,
     * calls setSegmentedToolContent, then adds it to the container. Initial
     * render is deferred to addNotify(), so the segments panel is empty
     * when setSegmentedToolContent fires. The content must still be applied
     * when the activity pane is created on first render.
     */
    @Test
    void testSetSegmentedToolContentBeforeInitialRender() {
        MessageBubble bubble = new MessageBubble(
                MessageType.tool_call, "", null, "Execution Steps (2)",
                MessageBubble.AvatarPosition.NONE);

        // Set segmented content BEFORE the bubble is added to a container
        // (simulating the ToolThoughtCombiner.combine path).
        List<CollapsibleToolPane.ToolSegment> blocks = List.of(
                new CollapsibleToolPane.ToolSegment("thought text", true, "Thinking"),
                new CollapsibleToolPane.ToolSegment("tool text", false, "TOOL: read"));
        bubble.setSegmentedToolContent(blocks);

        // Now mount the bubble to trigger addNotify → initial render.
        // Put the host in a window so it's "showing" — that's what triggers
        // addNotify on the bubble when add() is called.
        JPanel host = new JPanel();
        host.add(bubble);
        javax.swing.JFrame frame = new javax.swing.JFrame();
        frame.getContentPane().add(host);
        frame.pack();
        frame.setVisible(true);
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // After addNotify, the bubble must contain a CollapsibleActivityPane
        // with the segmented content (not an empty pane).
        // Locate the segments panel via reflection-free traversal: the bubble's
        // own children include the bubble panel which contains segments.
        java.awt.Component[] bubbleChildren = bubble.getComponents();
        assertTrue(bubbleChildren.length > 0, "Bubble should have a bubble panel child");
        // Walk into the content row → bubble panel → segments.
        CollapsibleActivityPane activityPane = findActivityPane(bubble);
        assertNotNull(activityPane, "Activity pane should be created from pending segmented content");
        // The segmented contentPanel should have children (the segment rows).
        // An empty unsegmented pane would just have a single JTextArea.
        assertTrue(activityPane.getComponentCount() > 0,
                "Activity pane should have content components");
        // The pane must contain 2 segment rows (one per ToolSegment).
        assertEquals(2, countSegmentRows(activityPane),
                "Activity pane should have 2 segment rows from the 2 blocks");
        frame.dispose();
    }

    /**
     * Counts the number of segment rows in a segmented activity pane by walking
     * into the contentPanel → multiPanel → segment wrappers.
     */
    private static int countSegmentRows(CollapsibleActivityPane pane) {
        // The contentPanel is the second child of the pane (after header).
        // It's a BorderLayout panel with the multiPanel in CENTER.
        // The multiPanel is a BoxLayout(Y_AXIS) panel with one wrapper per segment.
        // Note: header is also a JPanel with BorderLayout, so we must skip it
        // by requiring BoxLayout (multiPanel's layout) to avoid matching titlePanel.
        for (java.awt.Component c1 : pane.getComponents()) {
            if (c1 instanceof javax.swing.JPanel contentPanel
                    && contentPanel.getLayout() instanceof java.awt.BorderLayout) {
                for (java.awt.Component c2 : contentPanel.getComponents()) {
                    if (c2 instanceof javax.swing.JPanel multiPanel
                            && multiPanel.getLayout() instanceof javax.swing.BoxLayout) {
                        return multiPanel.getComponentCount();
                    }
                }
            }
        }
        return 0;
    }

    private static CollapsibleActivityPane findActivityPane(java.awt.Container container) {
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof CollapsibleActivityPane p) {
                return p;
            }
            if (c instanceof java.awt.Container child) {
                CollapsibleActivityPane found = findActivityPane(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}