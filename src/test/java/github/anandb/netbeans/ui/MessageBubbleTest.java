package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageBubbleTest {

    @Test
    void testAppendTextPreservesNewlinesForStreaming() {
        // Test the scenario where multi-line responses get concatenated incorrectly
        MessageBubble bubble = new MessageBubble("assistant", "");
        
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
        MessageBubble bubble = new MessageBubble("assistant", "");
        
        bubble.appendText("\n\nline1\n");
        assertEquals("\n\nline1\n", bubble.getRawText());
        
        bubble.appendText("line2\n");
        assertEquals("\n\nline1\nline2\n", bubble.getRawText());
    }

    @Test
    void testAppendTextWithEmptyStrings() {
        MessageBubble bubble = new MessageBubble("assistant", "");
        
        bubble.appendText("");
        assertEquals("", bubble.getRawText());
        
        bubble.appendText("hello");
        assertEquals("hello", bubble.getRawText());
        
        bubble.appendText("");
        assertEquals("hello", bubble.getRawText());
    }

    @Test
    void testAppendTextWithOnlyNewlines() {
        MessageBubble bubble = new MessageBubble("assistant", "");
        
        bubble.appendText("\n\n\n");
        assertEquals("\n\n\n", bubble.getRawText());
        
        bubble.appendText("hello");
        assertEquals("\n\n\nhello", bubble.getRawText());
    }
}