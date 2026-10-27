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
        String name,
        String uri,
        Resource resource,
        Annotations annotations
    ) {
        /** Payload of an embedded {@code resource} content block. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Resource(String uri, String mimeType, String text, String blob) {}

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
            if ("resource".equals(type)) {
                return "[File: " + fileName(resource != null ? resource.uri() : null) + "]";
            }
            if ("resource_link".equals(type)) {
                return "[File: " + (name != null ? name : fileName(uri)) + "]";
            }
            return "";
        }

        /**
         * Last path segment of a {@code file:} URI, used as the display name. Any
         * query or fragment is dropped first, so a revision-qualified URI reads as
         * the file it points at.
         */
        private static String fileName(String uri) {
            if (uri == null || uri.isBlank()) {
                return "attachment";
            }
            int cut = uri.indexOf('?');
            int hash = uri.indexOf('#');
            if (cut < 0 || (hash >= 0 && hash < cut)) {
                cut = hash;
            }
            String path = cut >= 0 ? uri.substring(0, cut) : uri;
            int slash = path.lastIndexOf('/');
            String tail = slash >= 0 ? path.substring(slash + 1) : path;
            return tail.isEmpty() ? path : tail;
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
