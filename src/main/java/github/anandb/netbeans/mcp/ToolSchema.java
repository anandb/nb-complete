package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.support.MapperSupplier;

/**
 * Fluent builder for MCP tool input schemas. Replaces the repeated
 * {@code createObjectNode()} / {@code putObject("properties")} /
 * {@code put("type", ...)} / {@code putArray("required")} dance with one line
 * per property.
 *
 * <pre>{@code
 * ObjectNode schema = ToolSchema.object()
 *         .str("filePath", "Absolute path to file")
 *         .integer("startLine", "Start line (1-indexed, inclusive)")
 *         .bool("recursive", "Whether to list recursively", false)
 *         .require("filePath")
 *         .build();
 * }</pre>
 *
 * <p>Property types are limited to the scalars the tool set actually uses:
 * {@code string}, {@code integer}, {@code number} and {@code boolean}.
 */
public final class ToolSchema {

    private final ObjectNode schema;
    private final ObjectNode properties;
    private final ArrayNode required;
    /** The property most recently added, for default-value chaining. */
    private ObjectNode lastProperty;

    private ToolSchema() {
        schema = MapperSupplier.get().createObjectNode();
        schema.put("type", "object");
        properties = schema.putObject("properties");
        required = MapperSupplier.get().createArrayNode();
    }

    /** Starts a new {@code {"type":"object"}} schema. */
    public static ToolSchema object() {
        return new ToolSchema();
    }

    /** Adds a {@code string} property. */
    public ToolSchema str(String name, String description) {
        return property(name, "string", description);
    }

    /** Adds an {@code integer} property. */
    public ToolSchema integer(String name, String description) {
        return property(name, "integer", description);
    }

    /** Adds an {@code integer} property carrying a default value. */
    public ToolSchema integer(String name, String description, int defaultValue) {
        return property(name, "integer", description).defaulted(defaultValue);
    }

    /** Adds a {@code number} property. */
    public ToolSchema number(String name, String description) {
        return property(name, "number", description);
    }

    /** Adds a {@code boolean} property. */
    public ToolSchema bool(String name, String description) {
        return property(name, "boolean", description);
    }

    /** Adds a {@code boolean} property carrying a default value. */
    public ToolSchema bool(String name, String description, boolean defaultValue) {
        return property(name, "boolean", description).defaulted(defaultValue);
    }

    /** Marks one or more properties as required. */
    public ToolSchema require(String... names) {
        for (String name : names) {
            required.add(name);
        }
        return this;
    }

    /**
     * Finishes the schema. An empty schema serializes as {@code {"type":"object"}}
     * (the {@code properties} key is dropped when no property was added), and
     * {@code required} is omitted when nothing was marked required — matching the
     * hand-written schemas this builder replaces.
     */
    public ObjectNode build() {
        if (properties.isEmpty()) {
            schema.remove("properties");
        }
        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema;
    }

    private ToolSchema property(String name, String type, String description) {
        ObjectNode node = properties.putObject(name);
        node.put("type", type);
        node.put("description", description);
        lastProperty = node;
        return this;
    }

    private ToolSchema defaulted(int value) {
        lastProperty.put("default", value);
        return this;
    }

    private ToolSchema defaulted(boolean value) {
        lastProperty.put("default", value);
        return this;
    }
}
