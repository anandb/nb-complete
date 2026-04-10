package ai.opencode.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Session(
    @JsonProperty("sessionId") String id,
    String title,
    String cwd,           // Matches server's "cwd"
    String directory,     // Legacy compatibility
    String parentID,
    SessionTime time,
    List<Object> mcpServers // Added to match server's "mcpServers"
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionTime(
        long created,
        long updated
    ) {}
}
