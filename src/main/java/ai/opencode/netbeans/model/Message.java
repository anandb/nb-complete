package ai.opencode.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
        List<Part> parts
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Completion(
        String text,
        List<ToolCall> toolCalls,
        String stopReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(
        String type,
        String text,
        String url,
        String filename
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
