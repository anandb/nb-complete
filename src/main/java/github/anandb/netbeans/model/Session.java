package github.anandb.netbeans.model;

import java.io.File;
import org.apache.commons.lang3.StringUtils;

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
    List<SessionConfigOption> configOptions,
    ModelsInfo models,
    ModesInfo modes
) {
    public String effectiveDirectory() {
        return cwd() != null ? cwd() : directory();
    }

    public String projectName() {
        String dir = effectiveDirectory();
        if (StringUtils.isBlank(dir)) {
            return null;
        }
        return new File(dir).getName();
    }

    /** Creates a copy with models and modes filled in. */
    public Session withModelsAndModes(ModelsInfo models, ModesInfo modes) {
        return new Session(id, title, cwd, directory, parentID, updatedAt,
                mcpServers, configOptions, models, modes);
    }
}