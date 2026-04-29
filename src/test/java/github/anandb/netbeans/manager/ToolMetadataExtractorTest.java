package github.anandb.netbeans.manager;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ToolMetadataExtractorTest {

    @Test
    void testStripMetadataRemovesComments() {
        String result = ToolMetadataExtractor.stripMetadata("hello <!-- comment --> world");
        assertEquals("hello  world", result);
    }

    @Test
    void testStripMetadataRemovesMultilineComments() {
        String result = ToolMetadataExtractor.stripMetadata("before <!-- line1\nline2\nline3 --> after");
        assertEquals("before  after", result);
    }

    @Test
    void testStripMetadataRemovesMetadataTags() {
        String result = ToolMetadataExtractor.stripMetadata("text <metadata>some meta</metadata> end");
        assertEquals("text  end", result);
    }

    @Test
    void testStripMetadataRemovesBothTypes() {
        String result = ToolMetadataExtractor.stripMetadata(
            "a <!-- comment --> b <metadata>data</metadata> c"
        );
        assertEquals("a  b  c", result);
    }

    @Test
    void testStripMetadataWithNoMetadata() {
        String result = ToolMetadataExtractor.stripMetadata("clean text no tags");
        assertEquals("clean text no tags", result);
    }

    @Test
    void testStripMetadataWithEmptyString() {
        String result = ToolMetadataExtractor.stripMetadata("");
        assertEquals("", result);
    }

    @Test
    void testStripMetadataWithNullString() {
        String result = ToolMetadataExtractor.stripMetadata(null);
        assertNull(result);
    }

    @Test
    void testStripMetadataWithOnlyWhitespace() {
        String result = ToolMetadataExtractor.stripMetadata("   ");
        assertEquals("   ", result);
    }

    @Test
    void testStripMetadataWithNestedMetadata() {
        String result = ToolMetadataExtractor.stripMetadata(
            "<metadata><!-- inner comment --></metadata>"
        );
        assertEquals("", result);
    }

    @Test
    void testExtractToolTitleWithSkillContent() {
        String result = ToolMetadataExtractor.extractToolTitle(
            "skill",
            "<skill_content name=\"test-skill\">some content</skill_content>", null
        );
        assertEquals("Tool test-skill", result);
    }

    @Test
    void testExtractToolTitleWithPath() {
        String result = ToolMetadataExtractor.extractToolTitle(
            "path",
            "<path>/some/long/path/here</path> content", null
        );
        assertEquals("Tool /some/long/path/here", result);
    }

    @Test
    void testExtractToolTitleWithNullMessageId() {
        String result = ToolMetadataExtractor.extractToolTitle(
            null,
            "<skill_content name=\"my-skill\">content</skill_content>", null
        );
        assertEquals("Tool my-skill", result);
    }

    @Test
    void testExtractToolTitleWithColon() {
        String result = ToolMetadataExtractor.extractToolTitle(
            "tool:subtype",
            "some text here", null
        );
        assertEquals("tool ", result);
    }

    @Test
    void testExtractToolTitleWithLongTag() {
        String longTag = "a".repeat(100);
        String result = ToolMetadataExtractor.extractToolTitle(longTag, "plain text", null);
        assertEquals("Tool ", result);
    }

    @Test
    void testExtractToolTitleWithPlainText() {
        String result = ToolMetadataExtractor.extractToolTitle(
            "tool",
            "just some plain text with no special tags", null
        );
        assertEquals("Tool ", result);
    }

    @Test
    void testExtractToolTitleFromRead() {
        String result = ToolMetadataExtractor.extractToolTitle(
            "read:322",
            "<path>/home/user/ab</path>", null
        );
        assertEquals("read /home/user/ab", result);
    }

    @Test
    void testExtractToolTitlePrefersSkillContentOverPath() {
        String result = ToolMetadataExtractor.extractToolTitle(
            "skill-name",
            "<path>/some/path</path> <skill_content name=\"skill-name\">content</skill_content>",
            null
        );
        assertEquals("Tool skill-name", result);
    }

    @Test
    void testExtractToolTitleWithKindFallback() {
        String result = ToolMetadataExtractor.extractToolTitle(
            null, "plain text", "customKind"
        );
        assertEquals("customKind ", result);
    }

    @Test
    void testExtractToolTitleAbbreviatesLongIdentifier() {
        String longIdent = "a".repeat(100);
        String result = ToolMetadataExtractor.extractToolTitle(
            "tag",
            "<skill_content name=\"" + longIdent + "\">content</skill_content>",
            null
        );
        assertTrue(result.length() < 120);
        assertTrue(result.contains("..."));
    }
}
