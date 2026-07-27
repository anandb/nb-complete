package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
    String id,
    String type, // "user" or "assistant"
    Prompt prompt,
    Completion completion,
    String state // "thinking", "sent", etc.
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prompt(
        String text,
        List<ContentPart> parts
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Completion(
        String text,
        List<ContentPart> parts,
        List<ToolCall> toolCalls,
        String stopReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentPart(
        String type,
        String text,
        String url,
        String filename,
        @JsonProperty("mimeType") String mimeType,
        String data,
        Annotations annotations
    ) {
        public String getDisplayText() {
            if (annotations != null) {
                if (annotations.audience() != null && annotations.audience().contains("assistant")) {
                    return "";
                }
                if (annotations.tags() != null && annotations.tags().contains("hidden")) {
                    return "";
                }
            }
            if ("text".equals(type)) {
                return text;
            }
            if ("image".equals(type)) {
                return "[Image: " + (filename != null ? filename : url) + "]";
            }
            if ("file".equals(type)) {
                return "[File: " + filename + "]";
            }
            return "";
        }
    }

    /** Display role for export: "user", "thought", or "assistant". */
    public String extractRole() {
        if ("user".equals(type())) return "user";
        if ("thinking".equals(state())) return "thought";
        return "assistant";
    }

    /** Concatenated display text from prompt/completion parts. */
    public String extractText() {
        StringBuilder sb = new StringBuilder();
        if ("user".equals(type())) {
            if (prompt() != null) {
                if (prompt().parts() != null && !prompt().parts().isEmpty()) {
                    for (ContentPart part : prompt().parts()) {
                        String pt = part.getDisplayText();
                        if (pt != null && !pt.isEmpty()) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(pt);
                        }
                    }
                } else if (prompt().text() != null) {
                    sb.append(prompt().text());
                }
            }
        } else {
            if (completion() != null) {
                if (completion().parts() != null && !completion().parts().isEmpty()) {
                    for (ContentPart part : completion().parts()) {
                        String pt = part.getDisplayText();
                        if (pt != null && !pt.isEmpty()) {
                            if (sb.length() > 0) sb.append("\n\n");
                            sb.append(pt);
                        }
                    }
                } else if (completion().text() != null) {
                    sb.append(completion().text());
                }
                
                if (completion().toolCalls() != null) {
                    for (ToolCall tc : completion().toolCalls()) {
                        if (sb.length() > 0) sb.append("\n\n");
                        sb.append("[Tool Call: ").append(tc.name()).append("]");
                    }
                }
            }
        }
        return sb.toString().strip();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Annotations(
        List<String> audience,
        List<String> tags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(
        String id,
        String name,
        String arguments,
        ToolResult result
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolResult(
        String content,
        boolean isError
    ) {}
}
