package github.anandb.netbeans.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Parses tool call JSON data from permission requests into file change
 * records suitable for display in the NetBeans diff viewer.
 * <p>
 * Handles formats:
 * <ul>
 *   <li>{@code args.oldString/newString} with {@code args.filePath}</li>
 *   <li>{@code rawInput.diff} with {@code rawInput.filePath}</li>
 *   <li>{@code arguments.oldString/newString} with {@code arguments.filePath}</li>
 *   <li>{@code content[]} blocks with {@code oldText/newText} or unified diff</li>
 * </ul>
 */
public final class ToolCallDiffParser {

    private ToolCallDiffParser() {}

    /** A single file change extracted from a tool call. */
    public record FileChange(
        String filePath,
        String oldContent,
        String newContent,
        char status   // 'M' modified, 'A' added, 'D' deleted
    ) {}

    /**
     * Extracts a file path from a tool call node. Checks multiple field
     * locations in priority order.
     */
    public static String extractFilePath(JsonNode toolCall) {
        // 1. args / arguments
        JsonNode args = toolCall.has("args") ? toolCall.get("args")
                : toolCall.has("arguments") ? toolCall.get("arguments") : null;
        if (args != null) {
            String fp = textField(args, "filePath");
            if (fp != null) return fp;
            fp = textField(args, "file_path");
            if (fp != null) return fp;
            fp = textField(args, "path");
            if (fp != null) return fp;
        }

        // 2. rawInput
        if (toolCall.has("rawInput")) {
            JsonNode ri = toolCall.get("rawInput");
            String fp = textField(ri, "filePath");
            if (fp != null) return fp;
            fp = textField(ri, "filepath");
            if (fp != null) return fp;
        }

        // 3. title — only use as file path if it contains a path separator
        //    (avoids false positives like "version 1.2.3" or "Chapter.3")
        //    Additionally, ensure it does not contain spaces, as AI action titles often
        //    contain spaces and slashes (e.g., "Fix bug / Update feature").
        String title = textField(toolCall, "title");
        if (title != null && !title.contains(" ") && (title.contains("/") || title.contains("\\"))) {
            return title;
        }

        return null;
    }

    /**
     * Parses a tool call node into a list of file changes.
     */
    public static List<FileChange> parse(JsonNode toolCall) {
        List<FileChange> result = new ArrayList<>();

        if (toolCall == null) return result;

        // 1. args.oldString/newString (most common for edit tools)
        JsonNode args = toolCall.has("args") ? toolCall.get("args")
                : toolCall.has("arguments") ? toolCall.get("arguments") : null;
        if (args != null && args.has("oldString") && args.has("newString")) {
            String oldS = args.get("oldString").asText("");
            String newS = args.get("newString").asText("");
            String fp = extractFilePath(toolCall);
            result.add(createChange(fp, oldS, newS));
            // If we found an oldString/newString pair, that's the primary
            // content. Continue to check content[] only for additional blocks.
        }

        // 2. rawInput.diff
        if (toolCall.has("rawInput")) {
            JsonNode ri = toolCall.get("rawInput");
            if (ri.has("diff") && isNotBlank(ri.get("diff").asText())) {
                String diffText = ri.get("diff").asText();
                String fp = extractFilePath(toolCall);
                DiffPair dp = parseUnifiedDiff(diffText);
                if (dp != null) {
                    // Only add if we didn't get it from oldString/newString
                    if (result.isEmpty()) {
                        result.add(createChange(fp, dp.oldContent, dp.newContent));
                    }
                }
            }
        }

        // 3. content[] blocks
        if (toolCall.has("content") && toolCall.get("content").isArray()) {
            String fp = extractFilePath(toolCall);
            for (JsonNode block : toolCall.get("content")) {
                if (!block.has("type")) continue;
                String type = block.get("type").asText();

                if ("diff".equals(type)) {
                    // Use has() + asText() directly — empty string is valid
                    // content (e.g., "oldText": "" for a new file).
                    String oldT = block.has("oldText") ? block.get("oldText").asText() : null;
                    String newT = block.has("newText") ? block.get("newText").asText() : null;
                    if (oldT != null && newT != null) {
                        result.add(createChange(fp, oldT, newT));
                        continue;
                    }
                    String patch = textField(block, "patch");
                    if (patch == null) patch = textField(block, "text");
                    if (patch != null) {
                        DiffPair dp = parseUnifiedDiff(patch);
                        if (dp != null) {
                            result.add(createChange(fp, dp.oldContent, dp.newContent));
                        }
                    }
                }
            }
        }

        // 4. Old-style: args.filePath with no oldString/newString
        //    Covers write-to-file which has only new content.
        if (result.isEmpty() && args != null && args.has("filePath")) {
            String fp = args.get("filePath").asText("");
            // For write/create tools, we only have new content
            String content = textField(args, "content");
            if (content == null) content = textField(args, "text");
            if (content != null) {
                result.add(createChange(fp, "", content));
            }
        }

        // Remove no-op entries where old and new are identical
        result.removeIf(fc -> fc.oldContent().equals(fc.newContent()));

        // Deduplicate identical file changes
        List<FileChange> deduped = new ArrayList<>(new java.util.LinkedHashSet<>(result));

        return deduped;
    }

    /** Creates a FileChange with inferred status. */
    private static FileChange createChange(String filePath, String oldContent, String newContent) {
        if (oldContent == null) oldContent = "";
        if (newContent == null) newContent = "";
        char status;
        if (oldContent.isEmpty() && !newContent.isEmpty()) {
            status = 'A'; // added
        } else if (!oldContent.isEmpty() && newContent.isEmpty()) {
            status = 'D'; // deleted
        } else if (!oldContent.equals(newContent)) {
            status = 'M'; // modified
        } else {
            status = 'M'; // treat identical as modified (shouldn't happen)
        }
        return new FileChange(filePath != null ? filePath : "unknown", oldContent, newContent, status);
    }

    /** Reads a text field from a JSON node, returning null if absent or blank. */
    private static String textField(JsonNode node, String field) {
        if (node.has(field)) {
            String val = node.get(field).asText();
            return isNotBlank(val) ? val : null;
        }
        return null;
    }

    // --- Unified diff parser ---

    private static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@\\s+-(\\d+)(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");

    public record DiffPair(String oldContent, String newContent) {}

    /**
     * Parses a unified diff string into old/new content by extracting hunk
     * context. Returns null if parsing fails.
     */
    static DiffPair parseUnifiedDiff(String diff) {
        if (diff == null || diff.isEmpty()) return null;

        List<String> oldLines = new ArrayList<>();
        List<String> newLines = new ArrayList<>();
        boolean inHunk = false;
        boolean hasHunk = false;

        for (String line : diff.split("\n")) {
            if (line.startsWith("--- ") || line.startsWith("+++ ")) {
                continue;
            }

            Matcher m = HUNK_HEADER.matcher(line);
            if (m.find()) {
                inHunk = true;
                hasHunk = true;
                int oldStart = Integer.parseInt(m.group(1));
                int newStart = Integer.parseInt(m.group(2));
                
                // Pad with empty lines to align the hunk to its absolute line number.
                // This ensures the diff viewer displays the correct line numbers.
                while (oldLines.size() < oldStart - 1) {
                    oldLines.add("");
                }
                while (newLines.size() < newStart - 1) {
                    newLines.add("");
                }
                continue;
            }

            if (!inHunk) continue;

            if (line.startsWith("+")) {
                newLines.add(line.substring(1));
            } else if (line.startsWith("-")) {
                oldLines.add(line.substring(1));
            } else if (line.startsWith(" ")) {
                String ctx = line.substring(1);
                oldLines.add(ctx);
                newLines.add(ctx);
            } else if (line.isEmpty()) {
                oldLines.add("");
                newLines.add("");
            }
        }

        if (!hasHunk || (oldLines.isEmpty() && newLines.isEmpty())) {
            return null;
        }

        return new DiffPair(
            String.join("\n", oldLines),
            String.join("\n", newLines)
        );
    }
}
