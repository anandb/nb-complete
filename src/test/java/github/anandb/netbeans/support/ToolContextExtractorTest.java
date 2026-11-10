package github.anandb.netbeans.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ToolContextExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void choosesArgumentFieldsAndTruncatesCommands() throws Exception {
        assertEquals("/a", extract("{\"args\":{\"filePath\":\"/a\",\"path\":\"/b\"}}"));
        assertEquals("/b", extract("{\"arguments\":{\"path\":\"/b\"}}"));
        assertEquals("https://example.test", extract("{\"args\":{\"url\":\"https://example.test\"}}"));
        assertEquals("hello...", ToolContextExtractor.extractToolContext(node("{\"args\":{\"command\":\"hello world\"}}"), 8));
        assertEquals("abc", ToolContextExtractor.truncateCommand("abcdef", 3));
        assertEquals("abcdef", ToolContextExtractor.truncateCommand("abcdef", 6));
        assertNull(ToolContextExtractor.truncateCommand(null, 2));
    }

    @Test
    void usesFallbackStringsLocationsAndContentPaths() throws Exception {
        assertEquals("note: a short useful value", extract("{\"args\":{\"note\":\"a short useful value\"}}"));
        assertEquals("/from/location", extract("{\"locations\":[{}, {\"path\":\"/from/location\"}]}"));
        assertEquals("/from/content", extract("{\"content\":[{\"path\":\"\"},{\"path\":\"/from/content\"}]}"));
    }

    @Test
    void usesPatternsAndPiPermissionTitleAsFallbacks() throws Exception {
        assertEquals("src/Foo.java", extract("{\"patterns\":[\"src/Foo.java\"]}"));
        assertEquals("run...", ToolContextExtractor.extractToolContext(
                node("{\"patterns\":[\"run this long command\"]}"), 6));
        assertEquals("echo hello", extract("{\"title\":\"Current agent requested bash command 'echo hello' (matched '*').\"}"));
        assertEquals("git status", extract("{\"rawInput\":{\"title\":\"Current agent requested bash command 'git status' (matched '*').\"}}"));
        assertNull(extract("{}"));
    }

    private static String extract(String json) throws Exception {
        return ToolContextExtractor.extractToolContext(node(json));
    }

    private static JsonNode node(String json) throws Exception {
        return MAPPER.readTree(json);
    }
}
