package github.anandb.netbeans.model;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public record ProcessedMessage(
        MessageType messageType,
        String text,
        String messageId,
        String kind,
        String toolTitle,
        String rawText,
        boolean streaming,
        String status) {

    public boolean isIgnorable() {
        if (messageType == MessageType.tool_call || messageType == MessageType.tool_call_update) {
            return isNotBlank(status) && (status.startsWith("pending") || status.startsWith("in_progress"));
        }

        return isIgnorable(messageType.roleName(), text);
    }

    public static boolean isIgnorable(String role, String text) {
        String trimmed = defaultIfBlank(text, "").trim().toLowerCase();
        if (trimmed.endsWith(".") || trimmed.endsWith("!")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return "tool".equals(role) && (trimmed.equals("completed") || trimmed.equals("failed") ||
                trimmed.equals("in-progress") || trimmed.equals("in progress") || trimmed.equals("in_progress") ||
                trimmed.equals("inprogress") || trimmed.equals("success") || trimmed.equals("done"));
    }

    public static ProcessedMessage createError(MessageType messageType, String text, String messageId, String kind) {
        return new Builder().messageType(messageType).text(text).messageId(messageId).kind(kind).rawText(text).build();
    }

    public static class Builder {
        private MessageType messageType;
        private String text = "";
        private String messageId;
        private String kind;
        private String toolTitle;
        private String rawText;
        private boolean streaming;
        private String status;

        public Builder messageType(MessageType messageType) {
            this.messageType = messageType;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder toolTitle(String toolTitle) {
            this.toolTitle = toolTitle;
            return this;
        }

        public Builder rawText(String rawText) {
            this.rawText = rawText;
            return this;
        }

        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public ProcessedMessage build() {
            return new ProcessedMessage(messageType, text, messageId, kind, toolTitle, rawText, streaming, status);
        }
    }
}
