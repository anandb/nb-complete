package ai.opencode.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Session(
    @JsonProperty("sessionId") String id,
    String title,
    String cwd,
    String directory,
    String parentID,
    @JsonProperty("updatedAt") String updatedAt,
    List<Object> mcpServers,
    List<SessionConfigOption> configOptions
) {}