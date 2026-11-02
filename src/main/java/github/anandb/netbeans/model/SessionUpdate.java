package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.model.ModelRecords.Agent;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.defaultString;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionUpdate(
    String jsonrpc,
    String method,
    @JsonProperty("params") Params params
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Params(
        String sessionId,
        UpdateData update
    ) {}

    public static final String JSONRPC_VERSION = "2.0";
    public static final String METHOD = "session/update";

    /** Centralizes textual turn-end synthesis; avoids positional construction at call sites. */
    public static SessionUpdate syntheticTurnEnd(String sessionId, MessageType type) {
        UpdateData data = new UpdateData(type, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
        return new SessionUpdate(JSONRPC_VERSION, METHOD, new Params(sessionId, data));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateData(
        @JsonProperty("sessionUpdate") MessageType type,
        String title,
        JsonNode rawInput,
        String messageId,
        JsonNode content,
        Message message,
        List<Agent> agents,
        List<Session> sessions,
        Boolean isThinking,
        // A string on tool_call updates, an object on goose's status_message; see statusText().
        JsonNode status,
        String kind,
        String toolCallId,
        JsonNode rawOutput,
        List<AvailableCommand> availableCommands,
        List<SessionConfigOption> configOptions,
        Long used,
        Long size,
        JsonNode entries,
        JsonNode locations,
        // Reported cost object (amount, currency) on usage_update; may be absent.
        JsonNode cost,
        // Per-message usage object on the goose custom message_usage update.
        JsonNode usage
    ) {

        /**
         * Tool calls send {@code status} as a string ("in_progress"/"completed"),
         * while goose's custom status_message sends an object. Anything that is
         * not textual is therefore not a tool-call status.
         */
        public String statusText() {
            return status != null && status.isTextual() ? status.asText() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
        String type,
        String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AvailableCommand(
        String name,
        String description,
        AvailableCommandInput input
    ) {
        @Override
        public String toString() {
            return "/" + name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AvailableCommandInput(
        String hint
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawInput(
        String command,
        String description,
        String tool,
        String path,
        String filePath,
        String filePattern
    ) {}

    // Convenience methods to maintain backward compatibility in some logic if needed
    public String type() {
        UpdateData ud = update();
        return ud != null && ud.type() != null ? ud.type().name() : null;
    }

    public Message message() {
        UpdateData ud = update();
        return ud != null ? ud.message() : null;
    }

    public Boolean isThinking() {
        UpdateData ud = update();
        return ud != null ? ud.isThinking() : null;
    }

    public JsonNode content() {
        UpdateData ud = update();
        return ud != null ? ud.content() : null;
    }

    public String messageId() {
        UpdateData ud = update();
        if (ud == null) return null;
        return ud.messageId() != null ? ud.messageId() : ud.toolCallId();
    }

    public String status() {
        UpdateData ud = update();
        return ud != null ? ud.statusText() : null;
    }

    /** The goose custom {@code message_usage} payload, or null on other updates. */
    public JsonNode usage() {
        UpdateData ud = update();
        return ud != null ? ud.usage() : null;
    }

    /** The notice/progress object of a goose custom {@code status_message}, or null. */
    public JsonNode statusMessage() {
        UpdateData ud = update();
        return ud != null && ud.status() != null && !ud.status().isTextual() ? ud.status() : null;
    }

    public String kind() {
        UpdateData ud = update();
        return ud != null ? ud.kind() : null;
    }

    public String toolCallId() {
        UpdateData ud = update();
        return ud != null ? ud.toolCallId() : null;
    }

    public String command() {
        UpdateData ud = update();
        if (ud == null || ud.rawInput() == null) return "";
        JsonNode ri = ud.rawInput();
        return ri.has("command") ? defaultString(ri.get("command").asText()) : "";
    }

    public UpdateData update() {
        return params != null ? params.update() : null;
    }
}
