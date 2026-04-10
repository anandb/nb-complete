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
        String messageId,
        JsonNode content,
        Message message,
        List<Agent> agents,
        List<Session> sessions,
        Boolean isThinking
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
    
    public UpdateData update() {
        return params != null ? params.update() : null;
    }
}
