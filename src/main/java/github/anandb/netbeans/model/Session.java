package github.anandb.netbeans.model;

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
) {
    public String effectiveDirectory() {
        return cwd() != null ? cwd() : directory();
    }

    public String projectName() {
        String dir = effectiveDirectory();
        if (dir == null || dir.isEmpty()) {
            return null;
        }
        return new java.io.File(dir).getName();
    }
}