package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageTest {

    @Test
    void testMessageCreation() {
        Message.Prompt prompt = new Message.Prompt("test prompt", null);
        Message.Completion completion = new Message.Completion("test completion", null, null, null);
        Message message = new Message("1", "user", prompt, completion, "thinking");

        assertEquals("1", message.id());
        assertEquals("user", message.type());
        assertEquals("test prompt", message.prompt().text());
        assertEquals("test completion", message.completion().text());
        assertEquals("thinking", message.state());
    }

    @Test
    void testMessageWithNulls() {
        Message message = new Message("2", "assistant", null, null, null);
        assertEquals("2", message.id());
        assertEquals("assistant", message.type());
        assertNull(message.prompt());
        assertNull(message.completion());
        assertNull(message.state());
    }

    @Test
    void testPromptCreation() {
        Message.Prompt prompt = new Message.Prompt("hello", null);
        assertEquals("hello", prompt.text());
    }

    @Test
    void testAgentCreation() {
        ModelRecords.Agent agent = new ModelRecords.Agent("bot", "description");
        assertEquals("bot", agent.name());
        assertEquals("description", agent.description());
    }

    @Test
    void embeddedResourceDisplaysFileNameFromUri() {
        Message.ContentPart part = new Message.ContentPart("resource", null, null, null, null, null,
                null, null, new Message.ContentPart.Resource("file:///tmp/paste_1_notes.md", "text/markdown",
                        "# Notes", null), null);
        assertEquals("[File: paste_1_notes.md]", part.getDisplayText());
    }

    @Test
    void resourceLinkDisplaysName() {
        Message.ContentPart part = new Message.ContentPart("resource_link", null, null, null, null, null,
                "notes.md", "file:///tmp/notes.md", null, null);
        assertEquals("[File: notes.md]", part.getDisplayText());
    }

    @Test
    void assistantAudienceTextIsHiddenFromBubbles() {
        Message.ContentPart part = new Message.ContentPart("text", "file body", null, null, null, null,
                null, null, null, new Message.Annotations(List.of("assistant"), null));
        assertEquals("", part.getDisplayText());
    }
}
