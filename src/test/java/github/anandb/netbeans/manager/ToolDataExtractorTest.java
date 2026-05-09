package github.anandb.netbeans.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDataExtractorTest {

    @Test
    void testStripMetadataRemovesComments() {
        String result = ToolDataExtractor.stripMetadata("hello <!-- comment --> world");
        assertEquals("hello  world", result);
    }

    @Test
    void testStripMetadataRemovesMultilineComments() {
        String result = ToolDataExtractor.stripMetadata("before <!-- line1\nline2\nline3 --> after");
        assertEquals("before  after", result);
    }

    @Test
    void testStripMetadataRemovesMetadataTags() {
        String result = ToolDataExtractor.stripMetadata("text <metadata>some meta</metadata> end");
        assertEquals("text  end", result);
    }

    @Test
    void testStripMetadataRemovesBothTypes() {
        String result = ToolDataExtractor.stripMetadata(
            "a <!-- comment --> b <metadata>data</metadata> c"
        );
        assertEquals("a  b  c", result);
    }

    @Test
    void testStripMetadataWithNoMetadata() {
        String result = ToolDataExtractor.stripMetadata("clean text no tags");
        assertEquals("clean text no tags", result);
    }

    @Test
    void testStripMetadataWithEmptyString() {
        String result = ToolDataExtractor.stripMetadata("");
        assertEquals("", result);
    }

    @Test
    void testStripMetadataWithNullString() {
        String result = ToolDataExtractor.stripMetadata(null);
        assertNull(result);
    }

    @Test
    void testStripMetadataWithOnlyWhitespace() {
        String result = ToolDataExtractor.stripMetadata("   ");
        assertEquals("   ", result);
    }

    @Test
    void testStripMetadataWithNestedMetadata() {
        String result = ToolDataExtractor.stripMetadata(
            "<metadata><!-- inner comment --></metadata>"
        );
        assertEquals("", result);
    }

    @Test
    void testExtractToolTitleWithSkillContent() {
        String result = ToolDataExtractor.extractToolTitle(
            "skill",
            "<skill_content name=\"test-skill\">some content</skill_content>", null
        );
        assertEquals("skill test-skill", result);
    }

    @Test
    void testExtractToolTitleWithPath() {
        String result = ToolDataExtractor.extractToolTitle(
            "path",
            "<path>/some/long/path/here</path> content", null
        );
        assertEquals("Tool /some/long/path/here", result);
    }

    @Test
    void testExtractToolTitleWithNullMessageId() {
        String result = ToolDataExtractor.extractToolTitle(
            null,
            "<skill_content name=\"my-skill\">content</skill_content>", null
        );
        assertEquals("skill my-skill", result);
    }

    @Test
    void testExtractToolTitleWithColon() {
        String result = ToolDataExtractor.extractToolTitle(
            "tool:subtype",
            "some text here", null
        );
        assertEquals("tool ", result);
    }

    @Test
    void testExtractToolTitleWithLongTag() {
        String longTag = "a".repeat(100);
        String result = ToolDataExtractor.extractToolTitle(longTag, "plain text", null);
        assertEquals("Tool ", result);
    }

    @Test
    void testExtractToolTitleWithPlainText() {
        String result = ToolDataExtractor.extractToolTitle(
            "tool",
            "just some plain text with no special tags", null
        );
        assertEquals("Tool ", result);
    }

    @Test
    void testExtractToolTitleFromRead() {
        String result = ToolDataExtractor.extractToolTitle(
            "read:322",
            "<path>/home/user/ab</path>", null
        );
        assertEquals("read /home/user/ab", result);
    }

    @Test
    void testExtractToolTitlePrefersSkillContentOverPath() {
        String result = ToolDataExtractor.extractToolTitle(
            "skill-name",
            "<path>/some/path</path> <skill_content name=\"skill-name\">content</skill_content>",
            null
        );
        assertEquals("skill skill-name", result);
    }

    @Test
    void testExtractToolTitleWithKindFallback() {
        String result = ToolDataExtractor.extractToolTitle(
            null, "plain text", "customKind"
        );
        assertEquals("customKind ", result);
    }

    @Test
    void testExtractToolTitleAbbreviatesLongIdentifier() {
        String longIdent = "a".repeat(100);
        String result = ToolDataExtractor.extractToolTitle(
            "tag",
            "<skill_content name=\"" + longIdent + "\">content</skill_content>",
            null
        );
        assertTrue(result.length() < 120);
        assertTrue(result.contains("..."));
    }
}
