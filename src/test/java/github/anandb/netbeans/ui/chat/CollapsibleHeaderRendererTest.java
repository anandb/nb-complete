package github.anandb.netbeans.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;

/**
 * The tool header fits its title to the width it actually got. A path is elided in the directory
 * part only — the file name is what identifies the call — and a title that fits is never touched.
 */
class CollapsibleHeaderRendererTest {

    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private static FontMetrics metrics() {
        return new JLabel().getFontMetrics(FONT);
    }

    private static int widthOf(String text) {
        return metrics().stringWidth(text);
    }

    @Test
    void aTitleThatFitsIsReturnedUnchanged() {
        String text = "/home/anand/workspace/nb-complete/MessageBubbleTest.java";

        assertEquals(text, CollapsibleHeaderRenderer.elideMiddle(text, metrics(), widthOf(text)));
        assertEquals(text, CollapsibleHeaderRenderer.elideMiddle(text, metrics(), widthOf(text) + 40));
    }

    @Test
    void aNarrowHeaderKeepsTheFileNameAndTrimsTheDirectory() {
        String text = "/home/anand/workspace/nb-complete/src/test/java/MessageBubbleTest.java";
        int budget = widthOf(text) / 2;

        String shown = CollapsibleHeaderRenderer.elideMiddle(text, metrics(), budget);

        assertTrue(shown.startsWith("/home/anand"), "the head stays: " + shown);
        assertTrue(shown.endsWith("/MessageBubbleTest.java"), "the file name survives: " + shown);
        assertTrue(shown.contains("\u2026"), "the elision is marked: " + shown);
        assertTrue(widthOf(shown) <= budget, "it fits the budget: " + widthOf(shown));
    }

    @Test
    void aCommandWithNoSeparatorIsMiddleElided() {
        String text = "abcdefghijklmnopqrstuvwxyz0123456789";
        int budget = widthOf(text) / 2;

        String shown = CollapsibleHeaderRenderer.elideMiddle(text, metrics(), budget);

        assertTrue(shown.startsWith("abc"), "keeps the head: " + shown);
        assertTrue(shown.endsWith("6789"), "keeps the tail: " + shown);
        assertTrue(shown.contains("\u2026"), "the elision is marked: " + shown);
        assertTrue(widthOf(shown) <= budget, "it fits the budget: " + shown);
    }

    @Test
    void anUnknownWidthLeavesTheTextAlone() {
        String text = "/a/very/long/path/to/SomeFile.java";

        // A pane that has not been laid out must not guess and abbreviate.
        assertEquals(text, CollapsibleHeaderRenderer.elideMiddle(text, metrics(), 0));
        assertEquals(text, CollapsibleHeaderRenderer.elideMiddle(text, metrics(), -1));
        assertNull(CollapsibleHeaderRenderer.elideMiddle(null, metrics(), 100));
        assertEquals(text, CollapsibleHeaderRenderer.elideMiddle(text, null, 100));
    }

    @Test
    void anExtremelyNarrowBudgetStillYieldsTheNameOrAnEllipsis() {
        String text = "/home/anand/workspace/nb-complete/MessageBubbleTest.java";

        String shown = CollapsibleHeaderRenderer.elideMiddle(text, metrics(), 20);

        assertFalse(shown.isBlank(), "never blank: " + shown);
        assertTrue(shown.length() < text.length());
    }
}
