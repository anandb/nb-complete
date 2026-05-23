package github.anandb.netbeans.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the package-private utility methods in AssistantTopComponent:
 * truncatePath, truncateCommand, and extractToolContext.
 */
class AssistantTopComponentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -----------------------------------------------------------------------
    // truncatePath
    // -----------------------------------------------------------------------

    @Test
    void testTruncatePathNull() {
        assertNull(AssistantTopComponent.truncatePath(null));
    }

    @Test
    void testTruncatePathEmpty() {
        assertEquals("", AssistantTopComponent.truncatePath(""));
    }

    @Test
    void testTruncatePathShort() {
        String shortPath = "/home/user/file.txt";
        assertSame(shortPath, AssistantTopComponent.truncatePath(shortPath));
    }

    @Test
    void testTruncatePathAtBoundary() {
        String path = "a".repeat(65);
        assertEquals(path, AssistantTopComponent.truncatePath(path));
    }

    @Test
    void testTruncatePathJustOverBoundary() {
        // 66 chars → "..." + last 62 chars = 65 chars total
        String path = "a".repeat(66);
        String result = AssistantTopComponent.truncatePath(path);
        assertEquals(65, result.length());
        assertTrue(result.startsWith("..."));
        assertEquals(path.substring(4), result.substring(3));
    }

    @Test
    void testTruncatePathLong() {
        String path = "a".repeat(100);
        String result = AssistantTopComponent.truncatePath(path);
        assertEquals(65, result.length());
        assertTrue(result.startsWith("..."));
        assertEquals(path.substring(38), result.substring(3));
    }

    @Test
    void testTruncatePathWithSpaces() {
        String path = "/home/user/projects/my important document.txt";
        assertEquals(path, AssistantTopComponent.truncatePath(path));
    }

    // -----------------------------------------------------------------------
    // truncateCommand
    // -----------------------------------------------------------------------

    @Test
    void testTruncateCommandNull() {
        assertNull(AssistantTopComponent.truncateCommand(null));
    }

    @Test
    void testTruncateCommandEmpty() {
        assertEquals("", AssistantTopComponent.truncateCommand(""));
    }

    @Test
    void testTruncateCommandShort() {
        String cmd = "ls -la";
        assertSame(cmd, AssistantTopComponent.truncateCommand(cmd));
    }

    @Test
    void testTruncateCommandAtBoundary() {
        String cmd = "a".repeat(80);
        assertEquals(cmd, AssistantTopComponent.truncateCommand(cmd));
    }

    @Test
    void testTruncateCommandJustOverBoundary() {
        // 81 chars → first 77 chars + "..."
        String cmd = "a".repeat(81);
        String result = AssistantTopComponent.truncateCommand(cmd);
        assertEquals(80, result.length());
        assertTrue(result.endsWith("..."));
        assertEquals(cmd.substring(0, 77), result.substring(0, 77));
    }

    @Test
    void testTruncateCommandLong() {
        String cmd = "a".repeat(100);
        String result = AssistantTopComponent.truncateCommand(cmd);
        assertEquals(80, result.length());
        assertTrue(result.endsWith("..."));
        assertEquals(cmd.substring(0, 77), result.substring(0, 77));
    }

    @Test
    void testTruncateCommandWithSpecialChars() {
        String cmd = "echo 'hello world' | grep hello && npm test -- --coverage";
        assertEquals(cmd, AssistantTopComponent.truncateCommand(cmd));
    }

    // -----------------------------------------------------------------------
    // extractToolContext
    // -----------------------------------------------------------------------

    @Test
    void testExtractContextNullArgs() {
        // toolCall with no "args" or "arguments" key
        JsonNode tc = MAPPER.createObjectNode();
        assertNull(AssistantTopComponent.extractToolContext(tc));
    }

    @Test
    void testExtractContextNonObjectArgs() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createArrayNode());
        assertNull(AssistantTopComponent.extractToolContext(tc));
    }

    @Test
    void testExtractContextFilePath() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("filePath", "/home/user/project/src/main/java/Test.java"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/home/user/project/src/main/java/Test.java", result);
    }

    @Test
    void testExtractContextFilePathUnderscore() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("file_path", "/home/user/data.txt"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/home/user/data.txt", result);
    }

    @Test
    void testExtractContextPath() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("path", "/some/directory"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/some/directory", result);
    }

    @Test
    void testExtractContextCommand() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("command", "npm test -- --coverage"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("npm test -- --coverage", result);
    }

    @Test
    void testExtractContextUrl() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("url", "https://example.com/api"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("https://example.com/api", result);
    }

    @Test
    void testExtractContextUri() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("uri", "file:///tmp/test"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("file:///tmp/test", result);
    }

    @Test
    void testExtractContextFilePathTakesPrecedenceOverCommand() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("filePath", "/path/to/file.java")
                        .put("command", "rm -rf /"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/path/to/file.java", result);
    }

    @Test
    void testExtractContextFilePathTakesPrecedenceOverUrl() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("filePath", "/important/file.txt")
                        .put("url", "https://example.com"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/important/file.txt", result);
    }

    @Test
    void testExtractContextFallbackToStringField() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("query", "find all java files")
                        .put("count", 42));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("query: find all java files", result);
    }

    @Test
    void testExtractContextFallbackSkipsShortStrings() {
        // Values ≤ 5 chars should be skipped in fallback
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("short", "abc")
                        .put("longEnough", "abcdef"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("longEnough: abcdef", result);
    }

    @Test
    void testExtractContextFallbackSkipsLongStrings() {
        // Values ≥ 120 chars should be skipped in fallback
        String longVal = "a".repeat(120);
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("tooLong", longVal)
                        .put("ok", "reasonable value"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("ok: reasonable value", result);
    }

    @Test
    void testExtractContextNoStringFieldsReturnsNull() {
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("count", 42)
                        .put("flag", true));
        assertNull(AssistantTopComponent.extractToolContext(tc));
    }

    @Test
    void testExtractContextArgumentsFallbackKey() {
        // Should handle "arguments" key as alternative to "args"
        JsonNode tc = MAPPER.createObjectNode()
                .set("arguments", MAPPER.createObjectNode()
                        .put("filePath", "/alt/path.txt"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/alt/path.txt", result);
    }

    @Test
    void testExtractContextTruncatesLongFilePath() {
        String longPath = "/a/" + "x".repeat(70);
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("filePath", longPath));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertTrue(result.length() <= 65, "Truncated path should be ≤ 65 chars");
        assertTrue(result.startsWith("..."), "Truncated path should start with ellipsis");
        assertTrue(result.endsWith(longPath.substring(longPath.length() - 62)),
                "Truncated path should end with last 62 chars of original");
    }

    @Test
    void testExtractContextTruncatesLongCommand() {
        String longCmd = "tool " + "a".repeat(80);
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("command", longCmd));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertTrue(result.length() <= 80, "Truncated command should be ≤ 80 chars");
        assertTrue(result.endsWith("..."), "Truncated command should end with ellipsis");
    }

    @Test
    void testExtractContextArgsWithUnknownKeyUsesFallback() {
        // "args" present but object has only non-standard keys
        JsonNode tc = MAPPER.createObjectNode()
                .set("args", MAPPER.createObjectNode()
                        .put("content", "some meaningful text here"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("content: some meaningful text here", result);
    }

    // -----------------------------------------------------------------------
    // extractToolContext — ACP permission format (rawInput, locations, patterns)
    // -----------------------------------------------------------------------

    @Test
    void testExtractContextRawInputFilePath() {
        // ACP format: rawInput with camelCase filePath
        JsonNode tc = MAPPER.createObjectNode()
                .set("rawInput", MAPPER.createObjectNode()
                        .put("filePath", "/home/user/project/src/main.java"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/home/user/project/src/main.java", result);
    }

    @Test
    void testExtractContextRawInputLowercaseFilepath() {
        // ACP format: rawInput with lowercase filepath (used by write/edit tools)
        JsonNode tc = MAPPER.createObjectNode()
                .set("rawInput", MAPPER.createObjectNode()
                        .put("filepath", "/home/user/output.log"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/home/user/output.log", result);
    }

    @Test
    void testExtractContextRawInputSnakeCaseFilePath() {
        // ACP format: rawInput with snake_case file_path
        JsonNode tc = MAPPER.createObjectNode()
                .set("rawInput", MAPPER.createObjectNode()
                        .put("file_path", "/home/user/data.csv"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/home/user/data.csv", result);
    }

    @Test
    void testExtractContextRawInputFilePathTakesPrecedenceOverLowercase() {
        // CamelCase filePath should take precedence over lowercase filepath
        JsonNode tc = MAPPER.createObjectNode()
                .set("rawInput", MAPPER.createObjectNode()
                        .put("filePath", "/primary/path.txt")
                        .put("filepath", "/secondary/path.txt"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/primary/path.txt", result);
    }

    @Test
    void testExtractContextRawInputPath() {
        // ACP format: rawInput with path key
        JsonNode tc = MAPPER.createObjectNode()
                .set("rawInput", MAPPER.createObjectNode()
                        .put("path", "/some/directory"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/some/directory", result);
    }

    @Test
    void testExtractContextRawInputCommand() {
        // ACP format: rawInput with command
        JsonNode tc = MAPPER.createObjectNode()
                .set("rawInput", MAPPER.createObjectNode()
                        .put("command", "ls -la /home/user"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("ls -la /home/user", result);
    }

    @Test
    void testExtractContextRawInputUrl() {
        // ACP format: rawInput with url
        JsonNode tc = MAPPER.createObjectNode()
                .set("rawInput", MAPPER.createObjectNode()
                        .put("url", "https://example.com/api"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("https://example.com/api", result);
    }

    @Test
    void testExtractContextRawInputUri() {
        // ACP format: rawInput with uri
        JsonNode tc = MAPPER.createObjectNode()
                .set("rawInput", MAPPER.createObjectNode()
                        .put("uri", "file:///tmp/test"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("file:///tmp/test", result);
    }

    @Test
    void testExtractContextRawInputFallbackToStringField() {
        // ACP format: rawInput with non-standard string field
        JsonNode tc = MAPPER.createObjectNode()
                .set("rawInput", MAPPER.createObjectNode()
                        .put("query", "find all java files")
                        .put("count", 42));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("query: find all java files", result);
    }

    @Test
    void testExtractContextRawInputEmptyFallsThroughToLocations() {
        // Empty rawInput should fall through to locations array
        ObjectNode tc = MAPPER.createObjectNode();
        tc.set("rawInput", MAPPER.createObjectNode());
        ArrayNode locs = MAPPER.createArrayNode();
        locs.add(MAPPER.createObjectNode().put("path", "/from/locations/file.txt"));
        tc.set("locations", locs);
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/from/locations/file.txt", result);
    }

    @Test
    void testExtractContextRawInputEmptyFallsThroughToPatterns() {
        // Empty rawInput with no locations should fall through to patterns
        ObjectNode tc = MAPPER.createObjectNode();
        tc.set("rawInput", MAPPER.createObjectNode());
        ArrayNode pats = MAPPER.createArrayNode();
        pats.add("/from/patterns/path.txt");
        tc.set("patterns", pats);
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/from/patterns/path.txt", result);
    }

    @Test
    void testExtractContextLocationsOnly() {
        // ACP format: only locations array (no rawInput)
        ObjectNode tc = MAPPER.createObjectNode();
        ArrayNode locs = MAPPER.createArrayNode();
        locs.add(MAPPER.createObjectNode().put("path", "/location/path.txt"));
        tc.set("locations", locs);
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/location/path.txt", result);
    }

    @Test
    void testExtractContextLocationsMultiplePathsUsesFirst() {
        // Multiple locations, should use first entry
        ObjectNode tc = MAPPER.createObjectNode();
        ArrayNode locs = MAPPER.createArrayNode();
        locs.add(MAPPER.createObjectNode().put("path", "/first/path.txt"));
        locs.add(MAPPER.createObjectNode().put("path", "/second/path.txt"));
        tc.set("locations", locs);
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/first/path.txt", result);
    }

    @Test
    void testExtractContextPatternsOnly() {
        // ACP format: only patterns array (no rawInput, no locations)
        ObjectNode tc = MAPPER.createObjectNode();
        ArrayNode pats = MAPPER.createArrayNode();
        pats.add("/home/user/from/patterns.txt");
        tc.set("patterns", pats);
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/home/user/from/patterns.txt", result);
    }

    @Test
    void testExtractContextPatternsCommandLooksLikePath() {
        // Pattern with slash is treated as path
        ObjectNode tc = MAPPER.createObjectNode();
        ArrayNode pats = MAPPER.createArrayNode();
        pats.add("npm run build");
        tc.set("patterns", pats);
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("npm run build", result);
    }

    @Test
    void testExtractContextPatternsNoSlashTreatedAsCommand() {
        // Pattern without slash is treated as command
        ObjectNode tc = MAPPER.createObjectNode();
        ArrayNode pats = MAPPER.createArrayNode();
        pats.add("ls");
        tc.set("patterns", pats);
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("ls", result);
    }

    @Test
    void testExtractContextRawInputTakesPrecedenceOverLocations() {
        // rawInput should be checked before locations
        ObjectNode tc = MAPPER.createObjectNode();
        tc.set("rawInput", MAPPER.createObjectNode()
                .put("filePath", "/from/rawInput/file.txt"));
        ArrayNode locs = MAPPER.createArrayNode();
        locs.add(MAPPER.createObjectNode().put("path", "/from/locations/file.txt"));
        tc.set("locations", locs);
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/from/rawInput/file.txt", result);
    }

    @Test
    void testExtractContextRawInputTakesPrecedenceOverPatterns() {
        // rawInput should be checked before patterns
        ObjectNode tc = MAPPER.createObjectNode();
        tc.set("rawInput", MAPPER.createObjectNode()
                .put("filePath", "/from/rawInput/file.txt"));
        ArrayNode pats = MAPPER.createArrayNode();
        pats.add("/from/patterns/file.txt");
        tc.set("patterns", pats);
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/from/rawInput/file.txt", result);
    }

    @Test
    void testExtractContextNoArgsNoRawInputNoLocationsNoPatterns() {
        // No context available at all
        JsonNode tc = MAPPER.createObjectNode();
        assertNull(AssistantTopComponent.extractToolContext(tc));
    }

    @Test
    void testExtractContextLocationsEmptyArray() {
        // Empty locations array should not match
        ObjectNode tc = MAPPER.createObjectNode();
        tc.set("locations", MAPPER.createArrayNode());
        assertNull(AssistantTopComponent.extractToolContext(tc));
    }

    @Test
    void testExtractContextPatternsEmptyArray() {
        // Empty patterns array should not match
        ObjectNode tc = MAPPER.createObjectNode();
        tc.set("patterns", MAPPER.createArrayNode());
        assertNull(AssistantTopComponent.extractToolContext(tc));
    }

    @Test
    void testExtractContextRawInputAndArgsMixed() {
        // args should take precedence over rawInput when both present
        ObjectNode tc = MAPPER.createObjectNode();
        tc.set("args", MAPPER.createObjectNode()
                .put("filePath", "/from/args/file.txt"));
        tc.set("rawInput", MAPPER.createObjectNode()
                .put("filePath", "/from/rawInput/file.txt"));
        String result = AssistantTopComponent.extractToolContext(tc);
        assertEquals("/from/args/file.txt", result);
    }
}
