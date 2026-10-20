package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = ModelsInfoDeserializer.class)
public record ModelsInfo(
    List<AvailableModel> availableModels,
    String currentModelId
) {}
