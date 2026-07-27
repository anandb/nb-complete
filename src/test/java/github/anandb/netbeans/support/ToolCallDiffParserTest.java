package github.anandb.netbeans.support;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.support.ToolCallDiffParser.DiffPair;
import github.anandb.netbeans.support.ToolCallDiffParser.FileChange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive tests for ToolCallDiffParser covering all input formats,
 * edge cases, and failure modes.
 */
class ToolCallDiffParserTest {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private static ObjectNode obj() { return MAPPER.createObjectNode(); }
    private static ArrayNode arr() { return MAPPER.createArrayNode(); }

    // ========================================================
    // parseUnifiedDiff
    // ========================================================

    @Test
    void parseUnifiedDiff_null() {
        assertTrue(ToolCallDiffParser.parseUnifiedDiff(null).isEmpty());
    }

    @Test
    void parseUnifiedDiff_empty() {
        assertTrue(ToolCallDiffParser.parseUnifiedDiff("").isEmpty());
    }

    @Test
    void parseUnifiedDiff_singleHunk() {
        String diff = "@@ -1,3 +1,4 @@\n a\n-b\n+c\n d\n";
        List<DiffPair> dps = ToolCallDiffParser.parseUnifiedDiff(diff);
        assertNotNull(dps);
        assertEquals("a\nb\nd", dps.get(0).oldContent());
        assertEquals("a\nc\nd", dps.get(0).newContent());
    }

    @Test
    void parseUnifiedDiff_additionsOnly() {
        String diff = "@@ -1,0 +1,2 @@\n+a\n+b\n";
        List<DiffPair> dps = ToolCallDiffParser.parseUnifiedDiff(diff);
        assertNotNull(dps);
        assertEquals("", dps.get(0).oldContent());
        assertEquals("a\nb", dps.get(0).newContent());
    }

    @Test
    void parseUnifiedDiff_deletionsOnly() {
        String diff = "@@ -1,2 +0,0 @@\n-a\n-b\n";
        List<DiffPair> dps = ToolCallDiffParser.parseUnifiedDiff(diff);
        assertNotNull(dps);
        assertEquals("a\nb", dps.get(0).oldContent());
        assertEquals("", dps.get(0).newContent());
    }

    @Test
    void parseUnifiedDiff_multipleHunks() {
        String diff = "@@ -1,2 +1,2 @@\n a\n-b\n+c\n@@ -5,3 +5,4 @@\n x\n-y\n+z\n w\n";
        List<DiffPair> dps = ToolCallDiffParser.parseUnifiedDiff(diff);
        assertEquals(2, dps.size());
        assertEquals("a\nb", dps.get(0).oldContent());
        assertEquals("a\nc", dps.get(0).newContent());
        assertEquals("x\ny\nw", dps.get(1).oldContent());
        assertEquals("x\nz\nw", dps.get(1).newContent());
    }

    @Test
    void parseUnifiedDiff_withGitHeaders() {
        String diff = "--- a/file.java\n+++ b/file.java\n@@ -1,3 +1,4 @@\n a\n-b\n+c\n d\n";
        List<DiffPair> dps = ToolCallDiffParser.parseUnifiedDiff(diff);
        assertNotNull(dps);
        assertEquals("a\nb\nd", dps.get(0).oldContent());
        assertEquals("a\nc\nd", dps.get(0).newContent());
    }

    @Test
    void parseUnifiedDiff_noHunkHeader() {
        String diff = " a\n-b\n+c\n";
        assertTrue(ToolCallDiffParser.parseUnifiedDiff(diff).isEmpty());
    }

    @Test
    void parseUnifiedDiff_contextOnly_noChanges() {
        // Context lines only, no + or - → returns DiffPair with equal content
        String diff = "@@ -1,2 +1,2 @@\n a\n b\n";
        List<DiffPair> dps = ToolCallDiffParser.parseUnifiedDiff(diff);
        assertFalse(dps.isEmpty());
        assertEquals("a\nb", dps.get(0).oldContent());
        assertEquals("a\nb", dps.get(0).newContent());
    }

    @Test
    void parseUnifiedDiff_trailingNewline() {
        String diff = "@@ -1,1 +1,1 @@\n-old\n+new\n";
        List<DiffPair> dps = ToolCallDiffParser.parseUnifiedDiff(diff);
        assertFalse(dps.isEmpty());
        assertEquals("old", dps.get(0).oldContent());
        assertEquals("new", dps.get(0).newContent());
    }

    @Test
    void parseUnifiedDiff_contentBeforeHunk() {
        String diff = "some header\n@@ -1,1 +1,2 @@\n a\n+b\n";
        List<DiffPair> dps = ToolCallDiffParser.parseUnifiedDiff(diff);
        assertFalse(dps.isEmpty());
        assertEquals("a", dps.get(0).oldContent());
        assertEquals("a\nb", dps.get(0).newContent());
    }

    @Test
    void parseUnifiedDiff_emptyLinesInHunk() {
        String diff = "@@ -1,2 +1,2 @@\n a\n-\n+\n";
        List<DiffPair> dps = ToolCallDiffParser.parseUnifiedDiff(diff);
        assertFalse(dps.isEmpty());
        assertEquals("a\n", dps.get(0).oldContent());
        assertEquals("a\n", dps.get(0).newContent());
    }

    // ========================================================
    // extractFilePath
    // ========================================================

    @Test
    void extractFilePath_fromArgsFilePath() {
        ObjectNode args = obj().put("filePath", "/src/main.java");
        ObjectNode tc = obj().set("args", args);
        assertEquals("/src/main.java", ToolCallDiffParser.extractFilePath(tc));
    }

    @Test
    void extractFilePath_fromArgsFilePathWithUnderscore() {
        ObjectNode args = obj().put("file_path", "/src/main.java");
        ObjectNode tc = obj().set("args", args);
        assertEquals("/src/main.java", ToolCallDiffParser.extractFilePath(tc));
    }

    @Test
    void extractFilePath_fromArgsPath() {
        ObjectNode args = obj().put("path", "/src/main.java");
        ObjectNode tc = obj().set("args", args);
        assertEquals("/src/main.java", ToolCallDiffParser.extractFilePath(tc));
    }

    @Test
    void extractFilePath_fromRawInput() {
        ObjectNode ri = obj().put("filePath", "/src/util.java");
        ObjectNode tc = obj().set("rawInput", ri);
        assertEquals("/src/util.java", ToolCallDiffParser.extractFilePath(tc));
    }

    @Test
    void extractFilePath_argsTakesPriority() {
        ObjectNode args = obj().put("filePath", "/from/args.java");
        ObjectNode ri = obj().put("filePath", "/from/raw.java");
        ObjectNode tc = obj();
        tc.set("args", args);
        tc.set("rawInput", ri);
        assertEquals("/from/args.java", ToolCallDiffParser.extractFilePath(tc));
    }

    @Test
    void extractFilePath_fromTitleWithSlash() {
        ObjectNode tc = obj().put("title", "/home/project/src/Main.java");
        assertEquals("/home/project/src/Main.java", ToolCallDiffParser.extractFilePath(tc));
    }

    @Test
    void extractFilePath_fromTitleWithDotOnly_noPathSeparator() {
        // Title with a dot but no path separator is not a valid file path
        ObjectNode tc = obj().put("title", "Main.java");
        assertNull(ToolCallDiffParser.extractFilePath(tc));
    }

    @Test
    void extractFilePath_nullWhenNotFound() {
        ObjectNode tc = obj().put("name", "write_file");
        assertNull(ToolCallDiffParser.extractFilePath(tc));
    }

    @Test
    void extractFilePath_titleWithoutSlashOrDot() {
        ObjectNode tc = obj().put("title", "Edit file");
        assertNull(ToolCallDiffParser.extractFilePath(tc));
    }

    @Test
    void extractFilePath_emptyArgsFilePath() {
        ObjectNode args = obj().put("filePath", "");
        ObjectNode tc = obj().set("args", args);
        assertNull(ToolCallDiffParser.extractFilePath(tc));
    }

    // ========================================================
    // parse — null / empty inputs
    // ========================================================

    @Test
    void parse_null() {
        assertTrue(ToolCallDiffParser.parse(null).isEmpty());
    }

    @Test
    void parse_emptyObject() {
        assertTrue(ToolCallDiffParser.parse(obj()).isEmpty());
    }

    // ========================================================
    // parse — args.oldString / newString
    // ========================================================

    @Test
    void parse_oldStringNewString_modified() {
        ObjectNode args = obj()
                .put("filePath", "/src/Main.java")
                .put("oldString", "foo")
                .put("newString", "bar");
        ObjectNode tc = obj().set("args", args);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("/src/Main.java", result.get(0).filePath());
        assertEquals("foo", result.get(0).oldContent());
        assertEquals("bar", result.get(0).newContent());
        assertEquals('M', result.get(0).status());
    }

    @Test
    void parse_oldStringNewString_added() {
        ObjectNode args = obj()
                .put("filePath", "/src/New.java")
                .put("oldString", "")
                .put("newString", "class New {}");
        ObjectNode tc = obj().set("args", args);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals('A', result.get(0).status());
    }

    @Test
    void parse_oldStringNewString_deleted() {
        ObjectNode args = obj()
                .put("filePath", "/src/Old.java")
                .put("oldString", "class Old {}")
                .put("newString", "");
        ObjectNode tc = obj().set("args", args);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals('D', result.get(0).status());
    }

    @Test
    void parse_argumentsVariant() {
        ObjectNode args = obj()
                .put("filePath", "/src/App.java")
                .put("oldString", "old")
                .put("newString", "new");
        ObjectNode tc = obj().set("arguments", args);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("/src/App.java", result.get(0).filePath());
        assertEquals('M', result.get(0).status());
    }

    @Test
    void parse_oldStringNewString_identical() {
        // Identical old and new content produces no change — filtered out
        ObjectNode args = obj()
                .put("filePath", "/src/Main.java")
                .put("oldString", "unchanged")
                .put("newString", "unchanged");
        ObjectNode tc = obj().set("args", args);
        assertTrue(ToolCallDiffParser.parse(tc).isEmpty());
    }

    // ========================================================
    // parse — rawInput.diff
    // ========================================================

    @Test
    void parse_rawInputDiff() {
        String diff = "@@ -1,1 +1,2 @@\n-old\n+new\n+extra\n";
        ObjectNode ri = obj().put("diff", diff);
        ObjectNode tc = obj().set("rawInput", ri);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("old", result.get(0).oldContent());
        assertEquals("new\nextra", result.get(0).newContent());
        assertEquals('M', result.get(0).status());
    }

    @Test
    void parse_rawInputDiff_withFilePath() {
        String diff = "@@ -1,1 +1,1 @@\n-a\n+b\n";
        ObjectNode ri = obj().put("filePath", "/src/Edit.java").put("diff", diff);
        ObjectNode tc = obj().set("rawInput", ri);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("/src/Edit.java", result.get(0).filePath());
    }

    @Test
    void parse_rawInputDiff_emptyDiff() {
        ObjectNode ri = obj().put("diff", "");
        ObjectNode tc = obj().set("rawInput", ri);
        assertTrue(ToolCallDiffParser.parse(tc).isEmpty());
    }

    @Test
    void parse_rawInputDiff_malformed() {
        ObjectNode ri = obj().put("diff", "not a diff");
        ObjectNode tc = obj().set("rawInput", ri);
        assertTrue(ToolCallDiffParser.parse(tc).isEmpty());
    }

    @Test
    void parse_rawInputDiff_overriddenByOldString() {
        String diff = "@@ -1,1 +1,1 @@\n-x\n+y\n";
        ObjectNode args = obj().put("oldString", "alpha").put("newString", "beta");
        ObjectNode ri = obj().put("diff", diff);
        ObjectNode tc = obj();
        tc.set("args", args);
        tc.set("rawInput", ri);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("alpha", result.get(0).oldContent());
        assertEquals("beta", result.get(0).newContent());
    }

    // ========================================================
    // parse — content[] blocks
    // ========================================================

    @Test
    void parse_contentDiff_oldTextNewText() {
        ObjectNode block = obj()
                .put("type", "diff")
                .put("oldText", "original")
                .put("newText", "updated");
        ObjectNode tc = obj().set("content", arr().add(block));
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("original", result.get(0).oldContent());
        assertEquals("updated", result.get(0).newContent());
    }

    @Test
    void parse_contentDiff_patch() {
        String patch = "@@ -1,1 +1,1 @@\n-old\n+new\n";
        ObjectNode block = obj().put("type", "diff").put("patch", patch);
        ObjectNode tc = obj().set("content", arr().add(block));
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("old", result.get(0).oldContent());
        assertEquals("new", result.get(0).newContent());
    }

    @Test
    void parse_contentDiff_textField() {
        String patch = "@@ -1,1 +1,1 @@\n-foo\n+bar\n";
        ObjectNode block = obj().put("type", "diff").put("text", patch);
        ObjectNode tc = obj().set("content", arr().add(block));
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("foo", result.get(0).oldContent());
        assertEquals("bar", result.get(0).newContent());
    }

    @Test
    void parse_contentText_ignored() {
        ObjectNode block = obj().put("type", "text").put("text", "just some text");
        ObjectNode tc = obj().set("content", arr().add(block));
        assertTrue(ToolCallDiffParser.parse(tc).isEmpty());
    }

    @Test
    void parse_contentMultipleDiffBlocks() {
        ObjectNode b1 = obj().put("type", "diff").put("oldText", "v1").put("newText", "v2");
        ObjectNode b2 = obj().put("type", "diff").put("oldText", "a").put("newText", "b");
        ObjectNode tc = obj().set("content", arr().add(b1).add(b2));
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(2, result.size());
        assertEquals("v1", result.get(0).oldContent());
        assertEquals("a", result.get(1).oldContent());
    }

    @Test
    void parse_contentDiff_oldTextNewTextWithNulls() {
        ObjectNode block = obj()
                .put("type", "diff")
                .put("oldText", "")
                .put("newText", "content");
        ObjectNode tc = obj().set("content", arr().add(block));
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals('A', result.get(0).status());
    }

    // ========================================================
    // parse — old-style write (args.filePath + content)
    // ========================================================

    @Test
    void parse_oldStyleWrite_content() {
        ObjectNode args = obj()
                .put("filePath", "/src/NewFile.java")
                .put("content", "class New {}");
        ObjectNode tc = obj().set("args", args);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("/src/NewFile.java", result.get(0).filePath());
        assertEquals("", result.get(0).oldContent());
        assertEquals("class New {}", result.get(0).newContent());
        assertEquals('A', result.get(0).status());
    }

    @Test
    void parse_oldStyleWrite_text() {
        ObjectNode args = obj()
                .put("filePath", "/src/Notes.md")
                .put("text", "hello");
        ObjectNode tc = obj().set("args", args);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).newContent());
        assertEquals('A', result.get(0).status());
    }

    @Test
    void parse_oldStyleWrite_noContent() {
        ObjectNode args = obj().put("filePath", "/src/Empty.java");
        ObjectNode tc = obj().set("args", args);
        assertTrue(ToolCallDiffParser.parse(tc).isEmpty());
    }

    @Test
    void parse_oldStyleWrite_overriddenByOldString() {
        ObjectNode args = obj()
                .put("filePath", "/src/Foo.java")
                .put("oldString", "before")
                .put("newString", "after")
                .put("content", "this should be ignored");
        ObjectNode tc = obj().set("args", args);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("before", result.get(0).oldContent());
        assertEquals("after", result.get(0).newContent());
    }

    // ========================================================
    // parse — content[] combined with other formats
    // ========================================================

    @Test
    void parse_oldStringPlusContentDiff() {
        ObjectNode args = obj()
                .put("filePath", "/src/A.java")
                .put("oldString", "a")
                .put("newString", "b");
        ObjectNode b1 = obj().put("type", "diff").put("oldText", "x").put("newText", "y");
        ObjectNode tc = obj();
        tc.set("args", args);
        tc.set("content", arr().add(b1));
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(2, result.size());
        assertEquals("/src/A.java", result.get(0).filePath());
    }

    @Test
    void parse_duplicateDiffs_areDeduplicated() {
        ObjectNode args = obj()
                .put("filePath", "/src/A.java")
                .put("oldString", "a")
                .put("newString", "b");
        ObjectNode b1 = obj().put("type", "diff").put("oldText", "a").put("newText", "b");
        ObjectNode tc = obj();
        tc.set("args", args);
        tc.set("content", arr().add(b1));
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("/src/A.java", result.get(0).filePath());
    }

    // ========================================================
    // parse — no file path fallback
    // ========================================================

    @Test
    void parse_noFilePath_fallbackToUnknown() {
        ObjectNode args = obj().put("oldString", "a").put("newString", "b");
        ObjectNode tc = obj().set("args", args);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("unknown", result.get(0).filePath());
    }

    @Test
    void parse_rawInputDiff_noFilePath() {
        String diff = "@@ -1,1 +1,1 @@\n-x\n+y\n";
        ObjectNode ri = obj().put("diff", diff);
        ObjectNode tc = obj().set("rawInput", ri);
        List<FileChange> result = ToolCallDiffParser.parse(tc);
        assertEquals(1, result.size());
        assertEquals("unknown", result.get(0).filePath());
    }

    // ========================================================
    // FileChange record
    // ========================================================

    @Test
    void fileChange_constructorAndAccessors() {
        FileChange fc = new FileChange("pom.xml", "<old/>", "<new/>", 'M');
        assertEquals("pom.xml", fc.filePath());
        assertEquals("<old/>", fc.oldContent());
        assertEquals("<new/>", fc.newContent());
        assertEquals('M', fc.status());
    }
}
