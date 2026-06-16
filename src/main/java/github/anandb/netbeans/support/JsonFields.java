package github.anandb.netbeans.support;

/**
 * Canonical JSON field name constants used across the ACP protocol layer.
 * Eliminates repeated string literals in serialization/deserialization code.
 */
public final class JsonFields {

    private JsonFields() {}

    // Session & identity
    public static final String SESSION_ID = "sessionId";
    public static final String MESSAGE_ID = "messageId";
    public static final String CONFIG_OPTIONS = "configOptions";

    // Message content
    public static final String TYPE = "type";
    public static final String CONTENT = "content";
    public static final String TEXT = "text";
    public static final String STATUS = "status";
    public static final String ERROR = "error";

    // Streaming & UI state
    public static final String STREAMING = "streaming";
    public static final String TOOL_TITLE = "toolTitle";

    // Role & model
    public static final String ROLE = "role";
    public static final String MODEL = "model";
    public static final String AGENT = "agent";
    public static final String THINKING = "thinking";

    // Tool call fields
    public static final String TOOL_CALL_ID = "toolCallId";
    public static final String TOOL_NAME = "toolName";
    public static final String COMMAND = "command";
    public static final String THINKING_SIGNATURE = "thinkingSignature";

    // Role constants
    public static final String ROLE_USER = "user";
    public static final String ROLE_ERROR = "error";
    public static final String ROLE_TOOL = "tool";

    // Type constants
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_FILE = "file";
}
