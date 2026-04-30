package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanEntry(
    String priority,
    String status,
    String content
) {}
