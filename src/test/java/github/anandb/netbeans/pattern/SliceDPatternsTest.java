package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SliceDPatternsTest {
    @Test
    void vcsTargets() {
        assertTrue(GitDiffTargetPattern.matches("main"));
        assertTrue(GitDiffTargetPattern.matches("HEAD~1"));
        assertTrue(GitDiffTargetPattern.matches("abc..def"));
        assertFalse(GitDiffTargetPattern.matches("--output=x"));
        assertTrue(HgDiffTargetPattern.matches("tip"));
        // Leading-dash rejection lives in VcsToolSupport.isSafeToken, not the charset.
        assertTrue(HgDiffTargetPattern.matches("--config"));
        assertTrue(SinceFilterPattern.matches("2 weeks ago"));
        assertFalse(SinceFilterPattern.matches("x; rm -rf"));
    }

    @Test
    void sqlMarkers() {
        assertTrue(SqlNamePattern.matcher("-- name: insert_session\n").find());
        var slot = SqlSlotPattern.matcher("VALUES ({{id}})");
        assertTrue(slot.find());
        assertEquals("id", slot.group(1));
    }

    @Test
    void versions() {
        assertTrue(OfferableVersionPattern.matches("1.2.3"));
        assertTrue(OfferableVersionPattern.matches("1.2.3-RELEASE"));
        assertFalse(OfferableVersionPattern.matches("1.2.3-M1"));
        assertEquals("1.2.3", ReleaseSuffixPattern.stripFrom("1.2.3-RELEASE"));
        assertEquals("1.2.3-M1", ReleaseSuffixPattern.stripFrom("1.2.3-M1"));
    }

    @Test
    void javaVersion() {
        var j = JavaVersionPattern.matcher("openjdk version \"21.0.5\" 2024-10-15");
        assertTrue(j.find());
        assertEquals("21", j.group(1));
    }

    @Test
    void ignoreAndNames() {
        assertTrue(SwapFilePattern.matches("foo.swp"));
        assertTrue(SwapFilePattern.matches("foo~"));
        assertFalse(SwapFilePattern.matches("foo.java"));
        assertTrue(HiddenNamePattern.isHidden(".svn"));
        assertFalse(HiddenNamePattern.isHidden(".gitignore"));
        assertTrue(HiddenNamePattern.isHidden("Thumbs.db"));
        assertTrue(ProjectNamePattern.matches("my-project_1.md"));
        assertFalse(ProjectNamePattern.matches(" bad name"));
    }

    @Test
    void pathSeparator() {
        assertEquals(System.getenv("PATH") == null ? 1 : 2,
                PathSeparatorPattern.split("a" + java.io.File.pathSeparator + "b").length > 1 ? 2 : 1);
    }
}
