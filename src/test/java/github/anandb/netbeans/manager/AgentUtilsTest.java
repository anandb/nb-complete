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
        String result = ToolParamsExtractor.extractToolTitle(
            "skill_name",
            "<skill_content name=\"test-skill\">some content</skill_content>"
        );
        assertNotNull(result);
    }

    @Test
    void testExtractToolTitleWithPath() {
        String result = ToolParamsExtractor.extractToolTitle(
            "path_id",
            "<path>/some/path/here</path> content"
        );
        assertNotNull(result);
    }

    @Test
    void testExtractToolTitleWithNullMessageId() {
        String result = ToolParamsExtractor.extractToolTitle(
            null,
            "<skill_content name=\"my-skill\">content</skill_content>"
        );
        assertNotNull(result);
    }

    @Test
    void testExtractToolTitleWithColon() {
        String result = ToolParamsExtractor.extractToolTitle(
            "tool:subtype",
            "some text here"
        );
        assertNotNull(result);
    }

    @Test
    void testExtractToolTitleWithLongTag() {
        String longTag = "a".repeat(100);
        String result = ToolParamsExtractor.extractToolTitle(longTag, "plain text");
        assertEquals("Tool ", result);
    }

    @Test
    void testExtractToolTitleWithPlainText() {
        String result = ToolParamsExtractor.extractToolTitle(
            "plain",
            "just some plain text with no special tags"
        );
        assertNotNull(result);
    }

    @Test
    void testExtractToolTitleFromRead() {
        String result = ToolParamsExtractor.extractToolTitle(
            "read:322",
            "<path>/home/user/ab</path>"
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