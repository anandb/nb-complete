package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSchemaTest {

    @Test
    void emptySchemaIsBareObject() {
        // ProjectToolProvider's list_projects schema has no properties and no
        // required array — the builder must reproduce that exactly.
        assertEquals("{\"type\":\"object\"}", ToolSchema.object().build().toString());
    }

    @Test
    void propertiesAndRequiredAreEmitted() {
        ObjectNode schema = ToolSchema.object()
                .str("filePath", "Absolute path to file")
                .integer("startLine", "Start line (1-indexed, inclusive)")
                .require("filePath")
                .build();

        assertEquals(
                "{\"type\":\"object\",\"properties\":{"
                        + "\"filePath\":{\"type\":\"string\",\"description\":\"Absolute path to file\"},"
                        + "\"startLine\":{\"type\":\"integer\",\"description\":\"Start line (1-indexed, inclusive)\"}},"
                        + "\"required\":[\"filePath\"]}",
                schema.toString());
    }

    @Test
    void allScalarTypesAndDefaults() {
        ObjectNode schema = ToolSchema.object()
                .str("a", "s")
                .integer("b", "i")
                .integer("c", "i", 120)
                .number("d", "n")
                .bool("e", "b")
                .bool("f", "b", false)
                .build();

        assertEquals("string", schema.at("/properties/a/type").asText());
        assertEquals("integer", schema.at("/properties/b/type").asText());
        assertEquals("integer", schema.at("/properties/c/type").asText());
        assertEquals(120, schema.at("/properties/c/default").asInt());
        assertEquals("number", schema.at("/properties/d/type").asText());
        assertEquals("boolean", schema.at("/properties/e/type").asText());
        assertEquals("boolean", schema.at("/properties/f/type").asText());
        assertTrue(schema.at("/properties/f/default").isBoolean());
        assertFalse(schema.at("/properties/f/default").asBoolean());
    }

    @Test
    void noRequiredKeyWhenNothingRequired() {
        ObjectNode schema = ToolSchema.object().str("repoDir", "Repository directory").build();
        assertFalse(schema.has("required"));
    }

    @Test
    void requiredIsOmittedButPropertiesKeptWhenEmpty() {
        ObjectNode schema = ToolSchema.object().str("only", "d").build();
        assertTrue(schema.has("properties"));
        assertFalse(schema.has("required"));
    }

    @Test
    void matchesHandWrittenCloseTaskSchema() {
        // The exact schema TaskToolProvider.registerCloseTask used to hand-build.
        var mapper = github.anandb.netbeans.support.MapperSupplier.get();
        ObjectNode old = mapper.createObjectNode();
        old.put("type", "object");
        ObjectNode props = old.putObject("properties");
        ObjectNode repoId = props.putObject("repoId");
        repoId.put("type", "string");
        repoId.put("description", "Id of the repository holding the task. Optional if only one repository exists.");
        ObjectNode taskId = props.putObject("taskId");
        taskId.put("type", "string");
        taskId.put("description", "Id of the task to close (as returned by add_task).");
        old.putArray("required").add("taskId");

        ObjectNode now = ToolSchema.object()
                .str("repoId", "Id of the repository holding the task. "
                        + "Optional if only one repository exists.")
                .str("taskId", "Id of the task to close (as returned by add_task).")
                .require("taskId")
                .build();

        assertEquals(old.toString(), now.toString());
    }

    @Test
    void matchesHandWrittenListDirectorySchemaWithDefault() {
        var mapper = github.anandb.netbeans.support.MapperSupplier.get();
        ObjectNode old = mapper.createObjectNode();
        old.put("type", "object");
        ObjectNode props = old.putObject("properties");
        ObjectNode dirPath = props.putObject("dirPath");
        dirPath.put("type", "string");
        dirPath.put("description", "Absolute path to directory");
        ObjectNode recursive = props.putObject("recursive");
        recursive.put("type", "boolean");
        recursive.put("description", "Whether to list recursively");
        recursive.put("default", false);
        old.putArray("required").add("dirPath");

        ObjectNode now = ToolSchema.object()
                .str("dirPath", "Absolute path to directory")
                .bool("recursive", "Whether to list recursively", false)
                .require("dirPath")
                .build();

        assertEquals(old.toString(), now.toString());
    }
}
