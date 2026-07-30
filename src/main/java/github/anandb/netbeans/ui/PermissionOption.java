package github.anandb.netbeans.ui;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parsed option from a permission request payload.
 */
record PermissionOption(String id, String name, String kind) {
    static PermissionOption fromJson(JsonNode opt) {
        String optionId = opt.has("optionId") ? opt.get("optionId").asText() : "";
        String name = opt.has("name") ? opt.get("name").asText() : optionId;
        String kind = opt.has("kind") ? opt.get("kind").asText() : "";
        return new PermissionOption(optionId, name, kind);
    }
}
