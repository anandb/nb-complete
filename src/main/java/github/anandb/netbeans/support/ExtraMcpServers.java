package github.anandb.netbeans.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses and validates the user-supplied extra MCP servers sent with {@code session/new} and
 * {@code session/load}. The wire format is strictly the ACP {@code mcpServers} array:
 * {@code http}/{@code sse} entries carry {@code type}, {@code name}, {@code url} and
 * {@code headers}; {@code stdio} entries carry {@code name}, {@code command}, {@code args}
 * and {@code env} (no {@code type} discriminator, as the ACP schema defines it).
 */
public final class ExtraMcpServers {

    /** Name of the embedded Beanbot server; extra entries must not reuse it. */
    public static final String INTERNAL_SERVER_NAME = "nb";

    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private static final TypeReference<List<Map<String, Object>>> SERVER_LIST =
            new TypeReference<>() { };

    public static final String HTTP_EXAMPLE = """
            [
              {
                "type": "http",
                "name": "example-http",
                "url": "https://mcp.example.com/mcp",
                "headers": [
                  { "name": "Authorization", "value": "Bearer YOUR_TOKEN" },
                  { "name": "X-API-Key", "value": "YOUR_API_KEY" }
                ]
              }
            ]""";

    public static final String STDIO_EXAMPLE = """
            [
              {
                "name": "example-stdio",
                "command": "/absolute/path/to/mcp-server",
                "args": ["--option", "value"],
                "env": [
                  { "name": "API_KEY", "value": "YOUR_API_KEY" },
                  { "name": "LOG_LEVEL", "value": "info" }
                ]
              }
            ]""";

    private ExtraMcpServers() {
    }

    /**
     * Parses a complete ACP {@code mcpServers} array.
     *
     * @param json user-edited JSON, may be blank
     * @return the server maps in the order supplied; empty for blank input
     * @throws IllegalArgumentException when the text is not a valid ACP server list
     */
    public static List<Map<String, Object>> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + rootMessage(e), e);
        }
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("The configuration must be a JSON array of MCP servers.");
        }
        Set<String> names = new HashSet<>();
        for (int i = 0; i < root.size(); i++) {
            JsonNode node = root.get(i);
            String where = "Server #" + (i + 1);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException(where + " must be a JSON object.");
            }
            String name = requireText(node, "name", where);
            if (INTERNAL_SERVER_NAME.equals(name)) {
                throw new IllegalArgumentException(
                        where + ": \"" + INTERNAL_SERVER_NAME + "\" is reserved for the internal Beanbot server.");
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("Duplicate MCP server name \"" + name + "\".");
            }
            validateServer(node, where);
        }
        return MAPPER.convertValue(root, SERVER_LIST);
    }

    /** Parses stored text, returning an empty list for blank or invalid content. */
    public static List<Map<String, Object>> parseStored(String json) {
        try {
            return parse(json);
        } catch (IllegalArgumentException e) {
            Logger.from(ExtraMcpServers.class).warn(
                    "Ignoring invalid stored extra MCP servers: {0}", e.getMessage());
            return List.of();
        }
    }

    /** Formats a server list as the pretty-printed ACP array. */
    public static String pretty(List<Map<String, Object>> servers) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(servers);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot format MCP servers: " + rootMessage(e), e);
        }
    }

    /**
     * Appends an example to the editor text while keeping the result a valid ACP array. Blank or
     * invalid text is replaced by the example; when the example's server name already exists, the
     * example replaces the text instead of producing a duplicate.
     */
    public static String insertExample(String currentJson, String exampleJson) {
        if (currentJson == null || currentJson.isBlank()) {
            return exampleJson;
        }
        try {
            List<Map<String, Object>> merged = new ArrayList<>(parse(currentJson));
            merged.addAll(parse(exampleJson));
            String text = pretty(merged);
            parse(text);
            return text;
        } catch (IllegalArgumentException e) {
            return exampleJson;
        }
    }

    private static void validateServer(JsonNode node, String where) {
        String type = node.hasNonNull("type") ? requireText(node, "type", where) : "stdio";
        switch (type) {
            case "stdio" -> validateStdio(node, where);
            case "http", "sse" -> validateHttp(node, where);
            default -> throw new IllegalArgumentException(
                    where + ": \"type\" must be \"http\", \"sse\" or \"stdio\".");
        }
    }

    private static void validateStdio(JsonNode node, String where) {
        requireText(node, "command", where);
        requireStringArray(node, "args", where);
        validatePairs(node, "env", where);
    }

    private static void validateHttp(JsonNode node, String where) {
        requireText(node, "url", where);
        validatePairs(node, "headers", where);
    }

    private static String requireText(JsonNode node, String field, String where) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    where + ": \"" + field + "\" must be a non-empty string.");
        }
        return value.asText();
    }

    private static void requireStringArray(JsonNode node, String field, String where) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(where + ": \"" + field + "\" must be an array of strings.");
        }
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException(
                        where + ": \"" + field + "\" must be an array of strings.");
            }
        }
    }

    private static void validatePairs(JsonNode node, String field, String where) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(
                    where + ": \"" + field + "\" must be an array of {name, value} objects.");
        }
        for (JsonNode item : value) {
            if (!item.isObject()) {
                throw new IllegalArgumentException(
                        where + ": \"" + field + "\" must be an array of {name, value} objects.");
            }
            requireText(item, "name", where + " \"" + field + "\"");
            requireText(item, "value", where + " \"" + field + "\"");
        }
    }

    private static String rootMessage(Throwable e) {
        String message = e.getMessage();
        return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
    }
}
