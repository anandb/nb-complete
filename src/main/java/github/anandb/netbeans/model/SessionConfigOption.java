package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionConfigOption(
    String id,
    String name,
    String description,
    String category,
    String type,
    String currentValue,
    List<SessionConfigSelectOption> options
) {}