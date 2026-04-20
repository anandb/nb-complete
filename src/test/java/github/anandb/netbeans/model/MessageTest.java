package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
        Agent agent = new Agent("bot", "description");
        assertEquals("bot", agent.name());
        assertEquals("description", agent.description());
    }
}
