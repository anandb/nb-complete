package ai.opencode.netbeans.model;

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
        String data
    ) {
        public String getDisplayText() {
            if ("text".equals(type)) return text;
            if ("image".equals(type)) return "[Image: " + (filename != null ? filename : url) + "]";
            if ("file".equals(type)) return "[File: " + filename + "]";
            return "";
        }
    }

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
