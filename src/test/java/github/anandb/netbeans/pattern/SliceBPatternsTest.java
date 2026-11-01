package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SliceBPatternsTest {
    @Test
    void codeFence() {
        var m = CodeFencePattern.matcher("```java\nfoo\n```");
        assertTrue(m.find());
        assertEquals("java", m.group(1));
    }

    @Test
    void sectionSplit() {
        assertEquals(2, SectionSplitPattern.split("a\n---\nb").length);
    }

    @Test
    void htmlStrip() {
        assertEquals("hi & <x>", HtmlStripPatterns.stripHtml("<b>hi</b> &amp; &lt;x&gt;"));
        assertEquals("expected:<1>", HtmlStripPatterns.stripHtml("expected:<1>"));
    }

    @Test
    void stash() {
        var idx = StashNamePattern.extractIndex("stash@{2}: WIP");
        assertTrue(idx.isPresent());
        assertEquals(2, idx.getAsInt());
        assertTrue(StashRefPattern.matches("stash@{0}: WIP"));
        assertFalse(StashRefPattern.matches("main"));
    }

    @Test
    void fatalPrefix() {
        assertEquals("oops\n", FatalPrefixPattern.stripFrom("fatal: oops\n"));
    }

    @Test
    void banner() {
        assertTrue(BannerPattern.matches("pi v0.84.4"));
        assertFalse(BannerPattern.matches("hello"));
        assertTrue(ContextBlockPattern.matcher("## Context\n- a\n").find());
        assertTrue(ExtensionsBlockPattern.matcher("## Extensions\n- a\n").find());
    }

    @Test
    void toolPrefix() {
        assertEquals("run tests", ToolPrefixPattern.stripPrefix("TOOL: run tests"));
        assertEquals("run tests", ToolPrefixPattern.stripPrefix("tool run tests"));
    }
}
