package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AvailableMode(
    String id,
    String name,
    String description
) {}
