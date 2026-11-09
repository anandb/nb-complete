package github.anandb.netbeans.ui.chat;

import github.anandb.netbeans.model.MessageType;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void testUserBubbleStripsMetadataAtConstruction() {
        // Non-streaming user bubbles (local echo, session reload) render the
        // constructor text directly and never go through finalizeStreaming,
        // so the strip must already happen in the constructor.
        String withMetadata = "<metadata>\n  <purpose>reference</purpose>\n"
                + "  <file_path>/tmp/x.java</file_path>\n  <cursor>1250</cursor>\n"
                + "</metadata>\n\nplease fix the bug";
        MessageBubble user = new MessageBubble(MessageType.user_message_chunk,
                withMetadata, null, null, MessageBubble.AvatarPosition.NONE);
        assertEquals("please fix the bug", user.getRawText().strip());
    }

    @Test
    void testAssistantBubbleKeepsRawText() {
        // Only user bubbles strip <metadata>; assistant text is untouched.
        String raw = "text with <metadata>x</metadata> inside";
        MessageBubble assistant = new MessageBubble(MessageType.agent_message_chunk,
                raw, null, null, MessageBubble.AvatarPosition.NONE);
        assertEquals(raw, assistant.getRawText());
    }
    @Test
    void userBubbleRidesARightAlignedRow() {
        MessageBubble bubble = new MessageBubble(MessageType.user_message_chunk,
                "hello", null, null, MessageBubble.AvatarPosition.LEFT);

        assertTrue(bubble.getComponent(0) instanceof MessageBubble.AlignedRow,
                "user content must sit in the capped row");
    }

    @Test
    void theAssistantBubbleSpansTheWholeThreadWidth() {
        MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk,
                "hello", null, null, MessageBubble.AvatarPosition.NONE);

        // The action-button overlay shares the content's grid cell, so the row
        // is not necessarily the first child.
        MessageBubble.AlignedRow row = null;
        for (Component c : bubble.getComponents()) {
            if (c instanceof MessageBubble.AlignedRow r) {
                row = r;
            }
        }
        assertTrue(row != null, "assistant content must sit in a full-width row");
        row.setSize(800, 100);
        row.doLayout();

        // No reading-measure cap: the answer gets the whole row.
        assertEquals(800, row.getComponent(0).getWidth());
        assertEquals(0, row.getComponent(0).getX());
    }

    @Test
    void toolBubbleIsNotCapped() {
        MessageBubble bubble = new MessageBubble(MessageType.tool_call,
                "step", null, "Execution Steps", MessageBubble.AvatarPosition.NONE);

        assertFalse(bubble.getComponent(0) instanceof MessageBubble.AlignedRow,
                "activity rows keep the full width");
    }

    @Test
    void alignedRowCapsAndRightAlignsAUserBubble() {
        JLabel child = laidOutChild(new MessageBubble.AlignedRow(0.86f, 912, true), 800);

        assertEquals(688, child.getWidth(), "86% of the row");
        assertEquals(112, child.getX(), "flush with the right edge");
    }

    @Test
    void theUserCapStopsAt912Px() {
        JLabel child = laidOutChild(new MessageBubble.AlignedRow(0.86f, 912, true), 2000);

        assertEquals(912, child.getWidth(), "absolute cap wins on a wide panel");
    }

    @Test
    void aFullWidthRowUsesTheWholeRow() {
        JLabel child = laidOutChild(MessageBubble.AlignedRow.fullWidth(), 800);

        assertEquals(800, child.getWidth(), "no ratio, no absolute cap");
        assertEquals(0, child.getX());
    }

    @Test
    void avatarIsPinnedLeftForEveryUserMessage() {
        assertEquals(MessageBubble.AvatarPosition.LEFT, BubbleFactory.resolveAvatarPosition("user"));
        assertEquals(MessageBubble.AvatarPosition.NONE, BubbleFactory.resolveAvatarPosition("assistant"));
    }

    private static JLabel laidOutChild(MessageBubble.AlignedRow row, int width) {
        JLabel child = new JLabel("content");
        child.setPreferredSize(new Dimension(500, 40));
        row.add(child);
        row.setSize(width, 100);
        row.doLayout();
        return child;
    }

}