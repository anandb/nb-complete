package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelsInfo(
    List<AvailableModel> availableModels,
    String currentModelId
) {}
