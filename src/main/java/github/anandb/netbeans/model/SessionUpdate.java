package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateData(
        @JsonProperty("sessionUpdate") MessageType type,
        String title,
        RawInput rawInput,
        String messageId,
        JsonNode content,
        Message message,
        List<Agent> agents,
        List<Session> sessions,
        Boolean isThinking,
        String status,
        String kind,
        String toolCallId,
        JsonNode rawOutput,
        List<AvailableCommand> availableCommands,
        List<SessionConfigOption> configOptions,
        Long used,
        Long size,
        JsonNode entries
    ) {}

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
        String description
    ) {}

    // Convenience methods to maintain backward compatibility in some logic if needed
    public String type() {
        UpdateData ud = update();
        return ud != null ? ud.type().name() : null;
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
        return ud != null ? ud.messageId() : null;
    }

    public String status() {
        UpdateData ud = update();
        return ud != null ? ud.status() : null;
    }

    public String kind() {
        UpdateData ud = update();
        return ud != null ? ud.kind() : null;
    }

    public String toolCallId() {
        UpdateData ud = update();
        return ud != null ? ud.toolCallId() : null;
    }

    public UpdateData update() {
        return params != null ? params.update() : null;
    }
}
