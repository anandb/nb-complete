package github.anandb.netbeans.support;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Manages the global opencode configuration located in the user home directory
 * ({@code ~/.config/opencode/opencode.json} or {@code opencode.jsonc}).
 *
 * <p>The plugin offers to write a starter file (the bundled template) when no
 * configuration exists or when an existing file holds nothing beyond the
 * {@code $schema} reference. Files with real content are never touched.
 * Unparseable files require explicit user consent before being replaced.
 */
public final class GlobalOpencodeConfig {

    private static final Logger LOG = Logger.from(GlobalOpencodeConfig.class);

    /** Parser that tolerates JSONC-style comments without corrupting strings
     *  that contain {@code //} (e.g. {@code "https://opencode.ai/config.json"}). */
    private static final JsonMapper PARSER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .build();

    private static final String SCHEMA_FIELD = "$schema";
    private static final String FILE_JSON = "opencode.json";
    private static final String FILE_JSONC = "opencode.jsonc";
    private static final String TEMPLATE_RESOURCE = "/github/anandb/netbeans/support/opencode.json.template";
    /** Test seam: overrides the config base dir (see {@link #configDir()}). */
    static final String CONFIG_HOME_PROP = "beanbot.opencode.config-home";

    /** Outcome of {@link #evaluate()} used to decide whether to offer setup. */
    public enum State {
        /** A real configuration exists; nothing to do. */
        REAL_CONTENT,
        /** Missing, empty, or {@code $schema}-only file; offer the starter file. */
        NEEDS_SETUP,
        /** An existing file cannot be parsed; ask before overwriting. */
        UNPARSEABLE
    }

    /** Result of {@link #evaluate()}: the state and the file to mention in the prompt. */
    public static final class CheckResult {
        public final State state;
        public final String fileName;

        CheckResult(State state, String fileName) {
            this.state = state;
            this.fileName = fileName;
        }
    }

    private GlobalOpencodeConfig() {}

    /**
     * Evaluates the global opencode configuration in the user home directory.
     */
    public static CheckResult evaluate() {
        File configDir = configDir();
        File json = new File(configDir, FILE_JSON);
        File jsonc = new File(configDir, FILE_JSONC);

        if (json.exists() || jsonc.exists()) {
            // A real configuration in either file wins: don't offer to replace
            // a broken/empty sibling (e.g. an unparseable opencode.json) when
            // a valid opencode.jsonc already holds the user's config.
            if (hasRealContent(json) || hasRealContent(jsonc)) {
                return new CheckResult(State.REAL_CONTENT, null);
            }
            if (isUnparseable(json)) {
                return new CheckResult(State.UNPARSEABLE, json.getName());
            }
            if (isUnparseable(jsonc)) {
                return new CheckResult(State.UNPARSEABLE, jsonc.getName());
            }
            return new CheckResult(State.NEEDS_SETUP,
                    jsonc.exists() && !json.exists() ? FILE_JSONC : FILE_JSON);
        }
        return new CheckResult(State.NEEDS_SETUP, FILE_JSON);
    }

    /**
     * Writes the starter configuration from the bundled template. The template
     * is written in place to the existing file ({@code opencode.json} by
     * default, or {@code opencode.jsonc} when that is the only file present),
     * so the user keeps a single config file rather than gaining a second one.
     * Callers must confirm with the user first when the current file is
     * unparseable.
     */
    public static void writeDefaultConfig() {
        try {
            File configDir = configDir();
            if (!configDir.exists() && !configDir.mkdirs()) {
                LOG.warn("Failed to create config directory: {0}", configDir);
                return;
            }
            File json = new File(configDir, FILE_JSON);
            File jsonc = new File(configDir, FILE_JSONC);
            // Write to the file the user already has. When both are present
            // (unusual) the canonical opencode.json wins and an empty/schema
            // -only opencode.jsonc that would shadow it is removed below.
            File target = jsonc.exists() && !json.exists() ? jsonc : json;
            if (target.exists() && hasRealContent(target)) {
                LOG.info("Skipping write; existing {0} has real content", target);
                return;
            }
            try (InputStream is = GlobalOpencodeConfig.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
                if (is == null) {
                    LOG.warn("opencode.json.template not found in resources");
                    return;
                }
                Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LOG.info("Wrote global opencode configuration: {0}", target);
            }
            if (target.equals(json) && jsonc.exists() && !hasRealContent(jsonc)) {
                if (jsonc.delete()) {
                    LOG.info("Removed empty/schema-only opencode.jsonc: {0}", jsonc);
                } else {
                    LOG.warn("Failed to delete {0}", jsonc);
                }
            }
        } catch (IOException ex) {
            LOG.warn("Error writing global opencode configuration", ex);
        }
    }

    private static File configDir() {
        // opencode resolves its global config dir via the XDG convention:
        // $XDG_CONFIG_HOME/opencode, defaulting to ~/.config/opencode. Respect
        // XDG_CONFIG_HOME when set so a non-default location is handled too.
        //
        // The CONFIG_HOME_PROP system property is a test seam (System.getenv is
        // read-only): it shadows the XDG lookup so tests can pin an isolated
        // directory regardless of the host environment.
        String override = System.getProperty(CONFIG_HOME_PROP);
        File base;
        if (override != null && !override.isBlank()) {
            base = new File(override);
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            base = (xdg != null && !xdg.isBlank())
                    ? new File(xdg)
                    : new File(System.getProperty("user.home"), ".config");
        }
        return new File(base, "opencode");
    }

    /** True when the file exists, is non-blank, and cannot be parsed as JSON/JSONC. */
    private static boolean isUnparseable(File file) {
        if (!file.exists()) {
            return false;
        }
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return false;
            }
            PARSER.readTree(text);
            return false;
        } catch (IOException ex) {
            LOG.warn("Cannot parse global opencode config {0}: {1}", file, ex.getMessage());
            return true;
        }
    }

    /**
     * True when the file carries a real configuration: it parses to an object
     * with at least one field other than {@code $schema}. Missing/blank files
     * and unparseable files are not treated as real content (the latter still
     * require user consent before they are replaced).
     */
    private static boolean hasRealContent(File file) {
        if (!file.exists()) {
            return false;
        }
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return false;
            }
            JsonNode root = PARSER.readTree(text);
            if (root == null || !root.isObject()) {
                return true; // non-object root — never overwrite
            }
            for (Map.Entry<String, JsonNode> field : root.properties()) {
                if (!SCHEMA_FIELD.equals(field.getKey())) {
                    return true;
                }
            }
            return false;
        } catch (IOException ex) {
            LOG.warn("Cannot read global opencode config {0}", file);
            return false;
        }
    }
}
