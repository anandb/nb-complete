package github.anandb.netbeans.ui.chat;

import github.anandb.netbeans.model.MessageType;
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
    void assistantBubbleRidesALeftAlignedRow() {
        MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk,
                "hello", null, null, MessageBubble.AvatarPosition.NONE);

        assertTrue(bubble.getComponent(0) instanceof MessageBubble.AlignedRow,
                "assistant content must sit in the capped row");
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
        JLabel child = laidOutChild(new MessageBubble.AlignedRow(0.72f, 760, true), 800);

        assertEquals(576, child.getWidth(), "72% of the row");
        assertEquals(224, child.getX(), "flush with the right edge");
    }

    @Test
    void alignedRowCapsAnAssistantBubbleOnTheLeft() {
        JLabel child = laidOutChild(new MessageBubble.AlignedRow(0.88f, 920, false), 800);

        assertEquals(704, child.getWidth(), "88% of the row");
        assertEquals(0, child.getX(), "flush with the left edge");
    }

    @Test
    void alignedRowHonoursTheReadingMeasureCap() {
        JLabel child = laidOutChild(new MessageBubble.AlignedRow(0.72f, 760, true), 2000);

        assertEquals(760, child.getWidth(), "absolute cap wins on a wide panel");
    }

    @Test
    void aTableDropsTheReadingMeasureCap() {
        MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk,
                "| Tool | Status |\n|------|--------|\n| mvn  | green  |",
                null, null, MessageBubble.AvatarPosition.NONE);

        assertTrue(bubble.isWideContent(), "a table must use the whole row, not the 88% cap");
    }

    @Test
    void aCodeBlockDropsTheReadingMeasureCap() {
        MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk,
                "Run this:\n\n```java\nint x = 1;\n```\n",
                null, null, MessageBubble.AvatarPosition.NONE);

        assertTrue(bubble.isWideContent(), "a code block must use the whole row, not the 88% cap");
        assertTrue(((MessageBubble.AlignedRow) bubble.getComponent(0)).isFullWidth(),
                "the row must release the cap, not only report wide content");
    }

    @Test
    void proseKeepsTheReadingMeasureCap() {
        MessageBubble bubble = new MessageBubble(MessageType.agent_message_chunk,
                "plain prose", null, null, MessageBubble.AvatarPosition.NONE);

        assertFalse(bubble.isWideContent());
    }

    @Test
    void alignedRowFullWidthReleasesTheCap() {
        MessageBubble.AlignedRow row = new MessageBubble.AlignedRow(0.88f, 920, false);
        JLabel child = laidOutChild(row, 800);
        assertEquals(704, child.getWidth());

        assertTrue(row.setFullWidth(true), "the mode change must ask for a new layout pass");
        row.doLayout();
        assertEquals(800, child.getWidth(), "full-width content ignores the ratio");
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