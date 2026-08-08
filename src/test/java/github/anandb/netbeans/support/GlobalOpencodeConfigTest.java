package github.anandb.netbeans.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GlobalOpencodeConfigTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Pin the config base dir to an isolated temp location via the test
        // seam, so tests are independent of the host's user.home / XDG_CONFIG_HOME.
        System.setProperty(GlobalOpencodeConfig.CONFIG_HOME_PROP,
                tempDir.resolve(".config").toString());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(GlobalOpencodeConfig.CONFIG_HOME_PROP);
    }

    private Path configDir() {
        return tempDir.resolve(".config").resolve("opencode");
    }

    @Test
    void noFilesMeansNeedsSetup() {
        assertEquals(GlobalOpencodeConfig.State.NEEDS_SETUP, GlobalOpencodeConfig.evaluate().state);
    }

    @Test
    void schemaOnlyJsonMeansNeedsSetup() throws IOException {
        Files.createDirectories(configDir());
        Files.writeString(configDir().resolve("opencode.json"),
                "{ \"$schema\": \"https://opencode.ai/config.json\" }");
        assertEquals(GlobalOpencodeConfig.State.NEEDS_SETUP, GlobalOpencodeConfig.evaluate().state);
    }

    @Test
    void schemaOnlyJsoncWithCommentsMeansNeedsSetup() throws IOException {
        Files.createDirectories(configDir());
        Files.writeString(configDir().resolve("opencode.jsonc"),
                "// starter\n{ \"$schema\": \"https://opencode.ai/config.json\" } // end\n");
        assertEquals(GlobalOpencodeConfig.State.NEEDS_SETUP, GlobalOpencodeConfig.evaluate().state);
    }

    @Test
    void realJsonMeansRealContent() throws IOException {
        Files.createDirectories(configDir());
        Files.writeString(configDir().resolve("opencode.json"),
                "{ \"$schema\": \"https://opencode.ai/config.json\", \"permission\": { \"read\": \"ask\" } }");
        assertEquals(GlobalOpencodeConfig.State.REAL_CONTENT, GlobalOpencodeConfig.evaluate().state);
    }

    @Test
    void realJsoncWithCommentsMeansRealContent() throws IOException {
        Files.createDirectories(configDir());
        Files.writeString(configDir().resolve("opencode.jsonc"),
                "// user config\n{ \"autoupdate\": false } // end\n");
        assertEquals(GlobalOpencodeConfig.State.REAL_CONTENT, GlobalOpencodeConfig.evaluate().state);
    }

    @Test
    void unparseableJsonMeansUnparseable() throws IOException {
        Files.createDirectories(configDir());
        Files.writeString(configDir().resolve("opencode.json"), "{ \"auth\": ");
        assertEquals(GlobalOpencodeConfig.State.UNPARSEABLE, GlobalOpencodeConfig.evaluate().state);
    }

    @Test
    void realJsoncWinsOverUnparseableJson() throws IOException {
        Files.createDirectories(configDir());
        Files.writeString(configDir().resolve("opencode.json"), "{ \"auth\": ");
        Files.writeString(configDir().resolve("opencode.jsonc"), "{ \"autoupdate\": false }");
        // A real config in opencode.jsonc must not be overshadowed by a broken
        // opencode.json — no prompt should be offered to replace it.
        assertEquals(GlobalOpencodeConfig.State.REAL_CONTENT, GlobalOpencodeConfig.evaluate().state);
    }

    @Test
    void writeDefaultConfigCreatesJsonFromTemplate() throws IOException {
        GlobalOpencodeConfig.writeDefaultConfig();
        Path json = configDir().resolve("opencode.json");
        assertTrue(Files.exists(json));
        String content = Files.readString(json);
        assertTrue(content.contains("\"$schema\""));
        assertTrue(content.contains("\"permission\""));
        assertEquals(GlobalOpencodeConfig.State.REAL_CONTENT, GlobalOpencodeConfig.evaluate().state);
    }

    @Test
    void writeDefaultConfigWritesTemplateToExistingJsonc() throws IOException {
        Files.createDirectories(configDir());
        Files.writeString(configDir().resolve("opencode.jsonc"),
                "{ \"$schema\": \"https://opencode.ai/config.json\" }");
        GlobalOpencodeConfig.writeDefaultConfig();
        // Template is written in place to the existing empty/schema-only file.
        assertTrue(Files.exists(configDir().resolve("opencode.jsonc")));
        assertFalse(Files.exists(configDir().resolve("opencode.json")));
        String content = Files.readString(configDir().resolve("opencode.jsonc"));
        assertTrue(content.contains("\"permission\""));
        assertEquals(GlobalOpencodeConfig.State.REAL_CONTENT, GlobalOpencodeConfig.evaluate().state);
    }

    @Test
    void writeDefaultConfigOverwritesUnparseableJson() throws IOException {
        Files.createDirectories(configDir());
        Files.writeString(configDir().resolve("opencode.json"), "{ \"auth\": ");
        GlobalOpencodeConfig.writeDefaultConfig();
        assertEquals(GlobalOpencodeConfig.State.REAL_CONTENT, GlobalOpencodeConfig.evaluate().state);
    }

    @Test
    void writeDefaultConfigDoesNotClobberRealJsonc() throws IOException {
        Files.createDirectories(configDir());
        Files.writeString(configDir().resolve("opencode.jsonc"), "{ \"autoupdate\": false }");
        GlobalOpencodeConfig.writeDefaultConfig();
        String content = Files.readString(configDir().resolve("opencode.jsonc"));
        assertTrue(content.contains("\"autoupdate\": false"));
        assertFalse(content.contains("\"permission\""));
        assertFalse(Files.exists(configDir().resolve("opencode.json")));
    }
}
