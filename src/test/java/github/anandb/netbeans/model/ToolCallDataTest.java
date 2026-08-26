package github.anandb.netbeans.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.anandb.netbeans.support.MapperSupplier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ToolCallDataTest {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    @Test
    void roundTripWithAllFields() throws Exception {
        String id = UUID.randomUUID().toString();
        String title = "title-" + UUID.randomUUID();
        String command = "cmd-" + UUID.randomUUID();
        String kind = "run_command";
        String status = "running";
        String text = "output-" + UUID.randomUUID();

        ToolCallData original = new ToolCallData(id, command);
        original.setTitle(title);
        original.setKind(kind);
        original.setStatus(status);
        original.setText(text);

        // Serialize
        String json = MAPPER.writeValueAsString(original);
        assertNotNull(json);

        // Deserialize
        ToolCallData restored = MAPPER.readValue(json, ToolCallData.class);
        assertNotNull(restored);

        // Verify all fields survived the round-trip
        assertEquals(id, restored.getToolCallId(), "toolCallId");
        assertEquals(command, restored.getCommand(), "command");
        assertEquals(title, restored.getTitle(), "title");
        assertEquals(kind, restored.getKind(), "kind");
        assertEquals(status, restored.getStatus(), "status");
        assertEquals(text, restored.getText(), "text");
    }
}
