package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;

class RemainingPatternsTest {

    @Test
    void releaseAndOfferableVersions() {
        assertTrue(ReleaseSuffixPattern.matcher("2.1-RELEASE").find());
        assertEquals("2.1", ReleaseSuffixPattern.stripFrom(" 2.1-RELEASE "));
        assertEquals("2.1", ReleaseSuffixPattern.stripFrom("2.1"));
        assertEquals("", ReleaseSuffixPattern.stripFrom(null));
        assertTrue(OfferableVersionPattern.matcher("2.1.RELEASE").matches());
        assertTrue(OfferableVersionPattern.matches(" 2.1-RELEASE "));
        assertFalse(OfferableVersionPattern.matches("2.x"));
        assertFalse(OfferableVersionPattern.matches(null));
    }

    @Test
    void globEscapesLiteralRegexCharactersAndMatchesNullSafely() {
        GlobPattern glob = GlobPattern.compile("a+b?.txt");
        assertTrue(glob.matcher("a+b1.txt").matches());
        assertTrue(glob.matches("a+b1.txt"));
        assertFalse(glob.matches("aaab1.txt"));
        assertFalse(glob.matches(null));
    }

    @Test
    void titleStashAndTablePatterns() {
        assertTrue(TitleSanitizePattern.matcher("bad name").find());
        assertEquals("bad_name", TitleSanitizePattern.sanitize("bad name"));
        assertEquals("", TitleSanitizePattern.sanitize(null));
        assertTrue(StashRefPattern.matcher("stash@{2}: WIP").matches());
        assertTrue(StashRefPattern.matches("stash@{2}: WIP"));
        assertFalse(StashRefPattern.matches(null));
        assertTrue(TableCellSplitPattern.matcher("a|b").find());
        assertArrayEquals(new String[]{"a", "b", "c"}, TableCellSplitPattern.split("a|b|c"));
        assertArrayEquals(new String[]{"a", "b|c"}, TableCellSplitPattern.split("a|b|c", 2));
        assertTrue(HtmlCommentPattern.matcher("a<!-- x -->b").find());
    }

    @Test
    void shellPathAndPriorityPatterns() {
        assertTrue(ShellOperatorPattern.matcher("echo $(id)").find());
        assertTrue(ShellOperatorPattern.containsOperator("echo a;b"));
        assertFalse(ShellOperatorPattern.containsOperator(null));
        assertTrue(PathSeparatorPattern.matcher("a" + File.pathSeparator + "b").find());
        assertArrayEquals(new String[]{"a", "b"}, PathSeparatorPattern.split(
                "a" + File.pathSeparator + "b"));
        assertTrue(PriorityPattern.matcher("A").matches());
        assertFalse(PriorityPattern.matches("a"));
        assertFalse(PriorityPattern.matches(null));
    }

    @Test
    void diffProjectAndHiddenNamePatterns() {
        assertTrue(HgDiffTargetPattern.matcher("tip").matches());
        assertFalse(HgDiffTargetPattern.matches("tag name"));
        assertFalse(HgDiffTargetPattern.matches(null));
        assertTrue(ProjectNamePattern.matcher("my-project").matches());
        assertTrue(ProjectNamePattern.matches("my.project"));
        assertFalse(ProjectNamePattern.matches("-bad"));
        assertFalse(ProjectNamePattern.matches(null));
        assertTrue(HiddenNamePattern.matcher(".DS_Store").find());
        assertTrue(HiddenNamePattern.isHidden(".env"));
        assertFalse(HiddenNamePattern.isHidden(".gitignore"));
        assertFalse(HiddenNamePattern.isHidden(null));
    }

    @Test
    void sectionAndPrefixes() {
        assertTrue(SectionSplitPattern.matcher("---").find());
        assertArrayEquals(new String[]{"one\n", "\ntwo"}, SectionSplitPattern.split("one\n---\ntwo"));
        assertTrue(ToolPrefixPattern.matcher("Tool: read").find());
        assertEquals("read", ToolPrefixPattern.stripPrefix("Tool: read"));
        assertEquals("", ToolPrefixPattern.stripPrefix(null));
        assertTrue(FatalPrefixPattern.matcher("fatal: failed").find());
        assertEquals("failed", FatalPrefixPattern.stripFrom("fatal: failed"));
        assertEquals("", FatalPrefixPattern.stripFrom(null));
    }

    @Test
    void swapGitStashAndHtmlPatterns() {
        assertTrue(SwapFilePattern.matcher("file.swp").matches());
        assertFalse(SwapFilePattern.matches("file.txt"));
        assertFalse(SwapFilePattern.matches(null));
        assertTrue(GitDiffTargetPattern.matcher("HEAD~1").matches());
        assertFalse(GitDiffTargetPattern.matches("bad ref"));
        assertFalse(GitDiffTargetPattern.matches(null));
        assertTrue(StashNamePattern.matcher("stash@{12}").find());
        assertEquals(12, StashNamePattern.extractIndex("stash@{12}: msg").getAsInt());
        assertTrue(StashNamePattern.extractIndex("none").isEmpty());
        assertTrue(StashNamePattern.extractIndex(null).isEmpty());
        assertTrue(HtmlStripPatterns.tagMatcher("<p>x</p>").find());
        assertEquals("<x> & ' \"", HtmlStripPatterns.stripHtml(
                "&lt;x&gt; &amp; &#39; &quot;"));
        assertNull(HtmlStripPatterns.stripHtml(null));
    }

    @Test
    void filtersAndMetadata() {
        assertTrue(SinceFilterPattern.matcher("2 days ago").matches());
        assertTrue(SinceFilterPattern.matches("last week"));
        assertFalse(SinceFilterPattern.matches("today; rm"));
        assertFalse(SinceFilterPattern.matches(null));
        assertTrue(MetadataBlockPattern.matcher("a<metadata>x</metadata>b").find());
        assertEquals("ab", MetadataBlockPattern.stripFrom("a<metadata>x</metadata>b"));
        assertNull(MetadataBlockPattern.stripFrom(null));
        assertTrue(TableSeparatorPattern.matcher(":---:").matches());
        assertFalse(TableSeparatorPattern.matches("x--"));
        assertFalse(TableSeparatorPattern.matches(null));
        assertTrue(BannerPattern.matcher("pi v1.0").matches());
        assertFalse(BannerPattern.matches("no-version"));
        assertFalse(BannerPattern.matches(null));
    }

    @Test
    void fileMatcherCoversShortNamesAndGlobMisses() {
        FileNameMatcher.Query plain = FileNameMatcher.compile("abcdef");
        assertFalse(FileNameMatcher.compile("").matches("abc", "src/abc"));
        assertFalse(FileNameMatcher.compile("").namePrefix("abc"));
        assertFalse(plain.namePrefix("abc"));
        assertFalse(plain.matches("abc.java", "src/other.java"));
        FileNameMatcher.Query glob = FileNameMatcher.compile("*.java");
        assertFalse(glob.matches("Thing.txt", "src/Thing.txt"));
        assertTrue(glob.matches("Thing.java", "src/Thing.java"));
        assertTrue(FileNameMatcher.compile("Thing?.java").matches("Thing1.java", "src/Thing1.java"));
        assertTrue(FileNameMatcher.compile("Thing*").matches("Thing1.java", "src/Thing1.java"));
        assertTrue(FileNameMatcher.compile("Thing?").matches("Thing1", "src/Thing1"));
        assertTrue(WhitespacePattern.matcher("a  b").find());
    }
}
