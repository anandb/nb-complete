package github.anandb.netbeans.support;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Extracts human-readable context strings from tool call arguments.
 * Pure utility with zero dependencies on any application layer.
 */
public final class ToolContextExtractor {

    private ToolContextExtractor() {}

    /**
     * Extracts a human-readable context string from tool call arguments.
     * Returns key info like file path, command, or URL, or null if no
     * meaningful context is found.
     *
     * Supports multiple formats:
     * <ul>
     *   <li>Standard tool call: {@code { args: { filePath: "..." } }}</li>
     *   <li>ACP permission format: {@code { rawInput: { filePath: "..." }, locations: [...], patterns: [...] }}</li>
     * </ul>
     */
    public static String extractToolContext(JsonNode toolCall) {
        return extractToolContext(toolCall, 80);
    }

    /**
     * Extracts a human-readable context string with a configurable command truncation limit.
     */
    public static String extractToolContext(JsonNode toolCall, int commandMaxLength) {
        JsonNode args = toolCall.has("args") ? toolCall.get("args")
                : toolCall.has("arguments") ? toolCall.get("arguments") : null;

        // If no args/arguments, try rawInput (ACP permission metadata)
        if (args == null || !args.isObject()) {
            args = toolCall.has("rawInput") ? toolCall.get("rawInput") : null;
        }

        if (args != null && args.isObject()) {
            String result = extractContextFromArgs(args, commandMaxLength);
            if (result != null) {
                return result;
            }
        }

        // Fallback: try locations array (ACP format)
        String locPath = extractFirstLocationPath(toolCall);
        if (locPath != null) {
            return truncatePath(locPath);
        }

        // Fallback: try patterns array (ACP format)
        String pattern = extractFirstPattern(toolCall);
        if (isNotBlank(pattern)) {
            if (pattern.contains("/") || pattern.contains(File.separator) || pattern.startsWith(".")) {
                return truncatePath(pattern);
            }
            return truncateCommand(pattern, commandMaxLength);
        }

        return null;
    }

    /**
     * Extracts context from a JSON object containing argument key-value pairs.
     * Checks in priority order: filePath, filepath, file_path, path, command,
     * url, uri, then falls back to the first short string value.
     */
    private static String extractContextFromArgs(JsonNode args, int commandMaxLength) {
        if (args.has("filePath")) {
            return truncatePath(args.get("filePath").asText());
        }
        if (args.has("filepath")) {
            return truncatePath(args.get("filepath").asText());
        }
        if (args.has("file_path")) {
            return truncatePath(args.get("file_path").asText());
        }
        if (args.has("path")) {
            return truncatePath(args.get("path").asText());
        }
        if (args.has("command")) {
            return truncateCommand(args.get("command").asText(), commandMaxLength);
        }
        if (args.has("url")) {
            return args.get("url").asText();
        }
        if (args.has("uri")) {
            return args.get("uri").asText();
        }

        // Fallback: show first string field value as brief context
        Iterator<Map.Entry<String, JsonNode>> fields = args.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isTextual()) {
                String val = entry.getValue().asText();
                if (val.length() > 5 && val.length() < 120) {
                    return entry.getKey() + ": " + val;
                }
            }
        }

        return null;
    }

    /**
     * Extracts the first path from the locations array (ACP permission format).
     * Locations are structured as {@code [{ path: "/path/to/file" }]}.
     */
    private static String extractFirstLocationPath(JsonNode toolCall) {
        if (toolCall.has("locations") && toolCall.get("locations").isArray()) {
            JsonNode locs = toolCall.get("locations");
            for (JsonNode loc : locs) {
                if (loc.has("path") && loc.get("path").isTextual()) {
                    return loc.get("path").asText();
                }
            }
        }
        return null;
    }

    /**
     * Extracts the first pattern from the patterns array (ACP permission format).
     */
    private static String extractFirstPattern(JsonNode toolCall) {
        if (toolCall.has("patterns") && toolCall.get("patterns").isArray()
                && toolCall.get("patterns").size() > 0) {
            JsonNode first = toolCall.get("patterns").get(0);
            if (first.isTextual()) {
                return first.asText();
            }
        }
        return null;
    }

    /**
     * Truncates a long file path for display, keeping the end of the path (filename).
     */
    public static String truncatePath(String path) {
        return path;
    }

    /**
     * Truncates a shell command for display, keeping the first {@code maxLength} chars.
     */
    public static String truncateCommand(String command, int maxLength) {
        if (command == null || command.length() <= maxLength) {
            return command;
        }
        if (maxLength <= 3) {
            return command.substring(0, maxLength);
        }
        return command.substring(0, maxLength - 3) + "...";
    }

    /**
     * Truncates a shell command for display, keeping the first ~80 chars.
     */
    public static String truncateCommand(String command) {
        return truncateCommand(command, 80);
    }
}
