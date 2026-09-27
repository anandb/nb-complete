package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-session metadata: the custom title, the context-usage snapshot, the
 * hidden flag and the project directory.
 *
 * <p>Moved here from {@code SessionManager} (where it was a nested
 * {@code static record}) so {@code contract/SessionStore} can name it —
 * {@code contract/} may import only from {@code model/}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionMetadata(
    @JsonProperty("title") String title,
    @JsonProperty("usage") String usage,
    @JsonProperty("hidden") boolean hidden,
    @JsonProperty("cwd") String cwd
) {}