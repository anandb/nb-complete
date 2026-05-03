package github.anandb.netbeans.model;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

public class ProcessedMessage {
    private MessageType messageType;
    private String text;
    private String messageId;
    private String kind;
    private String toolTitle;
    private String rawText;
    private MessageClassification classification;
    private boolean streaming;


    //<editor-fold defaultstate="collapsed" desc="constructors">
    public ProcessedMessage() {
    }

    public ProcessedMessage(MessageType messageType, String text, String messageId, String kind) {
        this.messageType = messageType;
        this.text = text;
        this.messageId = messageId;
        this.kind = kind;
        this.rawText = text;
        this.classification = new MessageClassification(messageType, kind);
    }

    public ProcessedMessage(MessageType messageType, String text, String messageId, String kind, String toolTitle) {
        this.messageType = messageType;
        this.text = text;
        this.messageId = messageId;
        this.kind = kind;
        this.toolTitle = toolTitle;
        this.rawText = text;
        this.classification = new MessageClassification(messageType, kind);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="get/set">
    public MessageType messageType() { return messageType; }
    public String text() { return text; }
    public String messageId() { return messageId; }
    public String kind() { return kind; }
    public String toolTitle() { return toolTitle; }
    public String rawText() { return rawText; }
    public MessageClassification classification() { return classification; }
    public boolean streaming() { return streaming; }


    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
    public void setText(String text) { this.text = text; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public void setKind(String kind) { this.kind = kind; }
    public void setToolTitle(String toolTitle) { this.toolTitle = toolTitle; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public void setStreaming(boolean streaming) { this.streaming = streaming; }

    //</editor-fold>


    public boolean isIgnorable() {
        return isIgnorable(messageType.roleName(), text);
    }

    public static boolean isIgnorable(String role, String text) {
// Disabled: streaming may send whitespace/partial content before real data arrives
//        if ("assistant".equals(role) && isBlank(text)) {
//            return true;
//        }

        String trimmed = defaultIfBlank(text, "").trim().toLowerCase();
        if (trimmed.endsWith(".") || trimmed.endsWith("!")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return "tool".equals(role) && (trimmed.equals("completed") || trimmed.equals("failed") ||
                trimmed.equals("in-progress") || trimmed.equals("in progress") || trimmed.equals("in_progress") ||
                trimmed.equals("inprogress") || trimmed.equals("success") || trimmed.equals("done"));
    }

    public static ProcessedMessage createError(MessageType messageType, String text, String messageId, String kind) {
        return new ProcessedMessage(messageType, text, messageId, kind);
    }
}
