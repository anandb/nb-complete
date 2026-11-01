package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SliceCPatternsTest {
    @Test
    void stripsCommentsAndMetadataBlocks() {
        assertEquals("hi ", HtmlCommentPattern.stripFrom("hi <!-- x -->"));
        assertEquals("hi ", MetadataBlockPattern.stripFrom("hi <metadata>y</metadata>"));
        assertNull(HtmlCommentPattern.stripFrom(null));
        assertNull(MetadataBlockPattern.stripFrom(null));
    }

    @Test
    void titlePrefixes() {
        var m = ToolTitlePrefixPattern.matcher("edit · /tmp/x");
        assertTrue(m.find());
        assertEquals("edit", m.group(1));
        assertTrue(McpTitlePrefixPattern.matcher("nb: read_file").find());
        assertFalse(ToolTitlePrefixPattern.matcher("plain").find());
    }

    @Test
    void contentMarkers() {
        assertTrue(SkillContentPattern.matcher("<skill_content name=\"ab\">").find());
        assertTrue(CompressedContextPattern.matcher("Compressed 12 messages into foo").find());
        assertTrue(PathTagPattern.matcher("<path>/some/long/path/x.java</path>").find());
        assertTrue(FilePathJsonPattern.matcher("\"filePath\": \"/some/long/x.java\"").find());
    }

    @Test
    void hunkHeader() {
        var m = HunkHeaderPattern.matcher("@@ -1,3 +4,5 @@");
        assertTrue(m.find());
        assertEquals("1", m.group(1));
        assertEquals("4", m.group(2));
    }

    @Test
    void piAgentCommand() {
        var m = PiAgentCommandPattern.matcher("Current agent requested bash command 'ls -la' (matched '*').");
        assertTrue(m.find());
        assertEquals("ls -la", m.group(1));
    }

    @Test
    void shellOperators() {
        assertTrue(ShellOperatorPattern.containsOperator("ls; rm -rf /"));
        assertTrue(ShellOperatorPattern.containsOperator("echo $(whoami)"));
        assertFalse(ShellOperatorPattern.containsOperator("ls -la"));
        assertFalse(ShellOperatorPattern.containsOperator(null));
    }

    @Test
    void glob() {
        assertTrue(GlobPattern.compile("*.java").matches("Foo.java"));
        assertFalse(GlobPattern.compile("*.java").matches("Foo.txt"));
        assertTrue(GlobPattern.compile("mkfs.*").matches("mkfs.ext4"));
        assertFalse(GlobPattern.compile("m?vn").matches("mvn"));
        assertTrue(GlobPattern.compile("m?vn").matches("mxvn"));
    }
}
