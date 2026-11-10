package github.anandb.netbeans.model;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelConversionTest {

    @Test
    void mapsWireMessageTypesToRoles() {
        assertEquals("thought", MessageType.agent_thought_chunk.roleName());
        assertEquals("tool", MessageType.tool_call.roleName());
        assertEquals("user", MessageType.user_message_chunk.roleName());
        assertEquals("assistant", MessageType.agent_message_chunk.roleName());
        assertEquals("error", MessageType.error_response.roleName());
        assertEquals("info", MessageType.info.roleName());
        assertNull(MessageType.plan.roleName());
    }

    @Test
    void populatesAndClearsModesFromServerResponse() {
        AvailableMode mode = new AvailableMode("code", "Code", "Write code");
        ModeAgentMapping.populate(new ModesInfo(List.of(mode, new AvailableMode(null, "ignored", null)), "code"));
        assertEquals(List.of(mode), ModeAgentMapping.getAvailableModes());
        ModeAgentMapping.populate(new ModesInfo(null, null));
        assertEquals(List.of(), ModeAgentMapping.getAvailableModes());
        ModeAgentMapping.populate(null);
        assertEquals(List.of(), ModeAgentMapping.getAvailableModes());
    }

    @Test
    void convertsAvailableModelsAndModesIntoConfigOptions() {
        ModelsInfo models = new ModelsInfo(List.of(new AvailableModel("m1", "Model 1", "desc")), "m1");
        ModesInfo modes = new ModesInfo(List.of(new AvailableMode("code", "Code", "desc")), "code");
        List<SessionConfigOption> options = ConfigOptionConverter.fromModelsAndModes(models, modes);
        assertEquals(2, options.size());
        assertEquals("m1", options.get(0).currentValue());
        assertEquals("code", options.get(1).currentValue());
        assertNull(ConfigOptionConverter.fromModelsAndModes(null, null));
        assertNull(ConfigOptionConverter.fromModelsAndModes(new ModelsInfo(null, null), new ModesInfo(null, null)));
    }

    @Test
    void transformsStoredUserAssistantAndThinkingMessages() {
        MessageTransformer transformer = new MessageTransformer();
        ProcessedMessage user = transformer.convert(new Message("u", "user",
                new Message.Prompt("prompt", List.of()), null, null));
        ProcessedMessage assistant = transformer.convert(new Message("a", "assistant", null,
                new Message.Completion("reply", null, null, null), null));
        ProcessedMessage thought = transformer.convert(new Message("t", "assistant", null,
                new Message.Completion("reason", null, null, null), "thinking"));
        assertEquals(MessageType.user_message_chunk, user.messageType());
        assertEquals("prompt", user.text());
        assertEquals(MessageType.agent_message_chunk, assistant.messageType());
        assertEquals(MessageType.agent_thought_chunk, thought.messageType());
    }
}
