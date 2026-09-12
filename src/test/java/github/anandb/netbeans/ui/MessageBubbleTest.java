package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.MessageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

}