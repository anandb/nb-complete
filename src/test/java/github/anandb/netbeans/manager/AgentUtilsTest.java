package github.anandb.netbeans.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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
}