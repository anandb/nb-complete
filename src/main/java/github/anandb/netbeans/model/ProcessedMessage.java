package github.anandb.netbeans.model;

import java.util.Set;
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

    private static final Set<String> TASK_STATUS_VALUES = Set.of(
        "completed", "failed", "in-progress", "in progress", "in_progress",
        "inprogress", "success", "done"
    );

    public boolean isIgnorable() {
        boolean isTool = messageType == MessageType.tool_call || messageType == MessageType.tool_call_update;
        if (isTool) {
            boolean pendingStatus = isNotBlank(status) && (status.startsWith("pending") || status.startsWith("in_progress"));
            boolean mcpTool = toolTitle != null && toolTitle.startsWith("mcp ");
            return pendingStatus || mcpTool;
        }

        return isIgnorable(messageType.roleName(), text);
    }

    public static boolean isIgnorable(String role, String text) {
        String taskStatus = defaultIfBlank(text, "").trim().toLowerCase();
        if (taskStatus.endsWith(".") || taskStatus.endsWith("!")) {
            taskStatus = taskStatus.substring(0, taskStatus.length() - 1);
        }

        return "tool".equals(role) && TASK_STATUS_VALUES.contains(taskStatus);
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
