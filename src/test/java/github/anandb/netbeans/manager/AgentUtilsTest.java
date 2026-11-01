package github.anandb.netbeans.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import github.anandb.netbeans.model.ModelRecords.MessageClassification;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.AgentUtils;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.ToolDataExtractor;
import org.junit.jupiter.api.Test;

class AgentUtilsTest {

    @Test
    void testExtractToolTitleWithSkillContent() {
        String result = ToolDataExtractor.extractToolTitle(
            "skill_name",
            "<skill_content name=\"test-skill\">some content</skill_content>", null, null
        );
        assertNotNull(result);
    }

    @Test
    void testExtractToolTitleWithPath() {
        String result = ToolDataExtractor.extractToolTitle(
            "path_id",
            "<path>/some/path/here</path> content", null, null
        );
        assertNotNull(result);
    }

    @Test
    void testExtractToolTitleWithNullMessageId() {
        String result = ToolDataExtractor.extractToolTitle(
            null,
            "<skill_content name=\"my-skill\">content</skill_content>", null, null
        );
        assertNotNull(result);
    }

    @Test
    void testExtractToolTitleWithColon() {
        String result = ToolDataExtractor.extractToolTitle(
            "tool:subtype",
            "some text here", null, null
        );
        assertNotNull(result);
    }

    @Test
    void testExtractToolTitleWithLongTag() {
        String longTag = "a".repeat(100);
        String result = ToolDataExtractor.extractToolTitle(longTag, "plain text", null, null);
        assertEquals("Tool ", result);
    }

    @Test
    void testExtractToolTitleWithPlainText() {
        String result = ToolDataExtractor.extractToolTitle(
            "plain",
            "just some plain text with no special tags", null, null
        );
        assertNotNull(result);
    }

    @Test
    void testExtractToolTitleFromRead() {
        String result = ToolDataExtractor.extractToolTitle(
            "read:322",
            "<path>/home/user/ab</path>", null, null
        );

        assertEquals("read /home/user/ab", result);
    }

    @Test
    void testCloseQuietlyWithNull() {
        AgentUtils.closeQuietly(null);
    }

    @Test
    void testCloseQuietlyWithCloseable() throws IOException {
        InputStream mock = new ByteArrayInputStream(new byte[0]);
        AgentUtils.closeQuietly(mock);
    }

    @Test
    void testCloseQuietlyThrowsException() {
        InputStream throwing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("test exception");
            }
        };
        AgentUtils.closeQuietly(throwing);
    }

    /**
     * goose reports no {@code kind} on the initial tool_call — it prefixes the title
     * instead ("edit · &lt;path&gt;"), which used to render as "Tool /path/File.java"
     * with no hint of what the call does.
     */
    @Test
    void gooseTitlePrefixBecomesTheToolKind() throws Exception {
        assertEquals("edit /p/File.java", titleOf("{\"sessionUpdate\":\"tool_call\","
                + "\"toolCallId\":\"c1\",\"title\":\"edit \\u00b7 /p/File.java\","
                + "\"rawInput\":{\"path\":\"/p/File.java\"}}"));

        assertEquals("shell cd /p && grep -n x", titleOf("{\"sessionUpdate\":\"tool_call\","
                + "\"toolCallId\":\"c2\",\"title\":\"shell \\u00b7 cd /p && grep -n x\","
                + "\"rawInput\":{\"command\":\"cd /p && grep -n x\"}}"));
    }

    @Test
    void mcpToolCallShowsMcpKindAndTarget() throws Exception {
        assertEquals("mcp /p/File.java", titleOf("{\"sessionUpdate\":\"tool_call\","
                + "\"toolCallId\":\"c3\",\"title\":\"nb: open pos\","
                + "\"rawInput\":{\"filePath\":\"/p/File.java\",\"line\":3}}"));
    }

    /** A follow-up update that carries {@code kind} + {@code locations} must not change the title. */
    @Test
    void kindAndLocationsUpdateMatchesTheInitialTitle() throws Exception {
        String initial = titleOf("{\"sessionUpdate\":\"tool_call\","
                + "\"toolCallId\":\"c4\",\"title\":\"edit \\u00b7 /p/File.java\","
                + "\"rawInput\":{\"path\":\"/p/File.java\"}}");
        String update = titleOf("{\"sessionUpdate\":\"tool_call_update\","
                + "\"toolCallId\":\"c4\",\"kind\":\"edit\","
                + "\"locations\":[{\"path\":\"/p/File.java\"}]}");

        assertEquals(initial, update, "the header must not flip when the update arrives");
    }

    private static String titleOf(String updateJson) throws Exception {
        SessionUpdate update = SessionUpdateDecoder.decode(
                MapperSupplier.get().readTree("{\"sessionId\":\"s\",\"update\":" + updateJson + "}"));
        MessageClassification mc = ToolDataExtractor.classify(update.update().type(), "",
                update.kind(), update.update().title());
        return ToolDataExtractor.extractToolTitle(update.messageId(), "", mc, update);
    }
}