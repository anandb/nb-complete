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
    List<Object> mcpServers,
    List<SessionConfigOption> configOptions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionTime(
        long created,
        long updated
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionConfigOption(
        String id,
        String name,
        String description,
        String category, // "mode", "model", "thought_level"
        String type,     // "select"
        String currentValue,
        List<SessionConfigSelectOption> options
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionConfigSelectOption(
        String value,
        String name,
        String description
    ) {}
}
