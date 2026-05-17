package github.anandb.netbeans.model;

import org.apache.commons.lang3.StringUtils;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.length;

public class ToolCallData {
    private String toolCallId;
    private String title;
    private String command;
    private String kind;
    private String status;
    private String text;

    public ToolCallData() {}

    public ToolCallData(String toolCallId, String command) {
        this.toolCallId = toolCallId;
        this.command = command;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getTitle() {
        return title;
    }

    public String setTitle(String title) {
        this.title = length(defaultString(title)) > length(defaultString(this.title)) ? title : this.title;
        return this.title;
    }

    public String getCommand() {
        return command;
    }

    public String setCommand(String command) {
        this.command = length(defaultString(command)) > length(defaultString(this.command)) ? command : this.command;
        return this.command;
    }

    public String getKind() {
        return kind;
    }

    public String setKind(String kind) {
        if (isNotBlank(kind)) {
            this.kind = kind;
        }
        
        return this.kind;
    }

    public String getStatus() {
        return status;
    }

    public String setStatus(String status) {
        if (isNotBlank(status) && !isDone()) {
            this.status = status;
        }

        return this.status;
    }

    public String getText() {
        return text;
    }

    public String setText(String text) {
        this.text = StringUtils.truncate(
            length(defaultString(text)) > length(defaultString(this.text)) ? text : this.text, 1024
        );
        return this.text;
    }

    public boolean isDone() {
        return ("completed".equals(this.status) || "failed".equals(this.status));
    }
}
