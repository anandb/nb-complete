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

    private String originalUserHome;

    @BeforeEach
    void setUp() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
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
