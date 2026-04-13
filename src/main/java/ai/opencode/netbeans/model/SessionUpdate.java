package ai.opencode.netbeans.model;

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
        @JsonProperty("sessionUpdate") String type,
        String title,
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
        List<SessionConfigOption> configOptions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AvailableCommand(
        String name,
        String description,
        AvailableCommandInput input
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AvailableCommandInput(
        String hint
    ) {}

    // Convenience methods to maintain backward compatibility in some logic if needed
    public String type() {
        UpdateData ud = update();
        return ud != null ? ud.type() : null;
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

    public List<SessionConfigOption> configOptions() {
        UpdateData ud = update();
        return ud != null ? ud.configOptions() : null;
    }

    public String messageId() {
        UpdateData ud = update();
        return ud != null ? ud.messageId() : null;
    }

    public String title() {
        UpdateData ud = update();
        return ud != null ? ud.title() : null;
    }

    public String status() {
        UpdateData ud = update();
        return ud != null ? ud.status() : null;
    }

    public JsonNode rawOutput() {
        UpdateData ud = update();
        return ud != null ? ud.rawOutput() : null;
    }

    public UpdateData update() {
        return params != null ? params.update() : null;
    }
}
