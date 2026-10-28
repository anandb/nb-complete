package github.anandb.netbeans.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;

/**
 * Guards the tooltip wrapping contract: every options tooltip is HTML with a pixel width
 * derived from the LAF font, long tokens get zero-width breaks, and values that are
 * already HTML are passed through untouched.
 */
class ACPOptionsPanelTooltipTest {

    @Test
    void plainTextIsWrappedAtTheMeasuredWidth() {
        String wrapped = ACPOptionsPanel.wrapTooltip("Short hint.");

        assertTrue(wrapped.startsWith("<html><body style='width:"), wrapped);
        assertTrue(wrapped.endsWith("</body></html>"), wrapped);
        assertTrue(wrapped.contains(">Short hint.</body>"), wrapped);

        int width = widthOf(wrapped);
        assertTrue(width >= 420 && width <= 900, "unexpected tooltip width: " + width);
    }

    @Test
    void newlinesBecomeLineBreaks() {
        assertTrue(ACPOptionsPanel.wrapTooltip("first\nsecond").contains("first<br>second"));
    }

    @Test
    void markupIsEscaped() {
        String wrapped = ACPOptionsPanel.wrapTooltip("use <b> and & here");

        assertTrue(wrapped.contains("&lt;b&gt;"), wrapped);
        assertTrue(wrapped.contains("&amp;"), wrapped);
        assertFalse(wrapped.contains("<b>"), wrapped);
    }

    @Test
    void existingHtmlIsNotWrappedTwice() {
        String html = "<html><body>already wrapped</body></html>";

        assertEquals(html, ACPOptionsPanel.wrapTooltip(html));
    }

    @Test
    void longPathsGetZeroWidthBreaks() {
        String wrapped = ACPOptionsPanel.wrapTooltip("/usr/local/bin/very-long-harness-binary-name/acp-server");

        assertTrue(wrapped.indexOf('\u200B') > 0, wrapped);
    }

    @Test
    void shortWordsGetNoZeroWidthBreaks() {
        String wrapped = ACPOptionsPanel.wrapTooltip("Default: acp");

        assertEquals(-1, wrapped.indexOf('\u200B'), wrapped);
    }

    @Test
    void scopeMarkersKeepTheirLeadingSpace() {
        // Escaped in Bundle.properties as "\ \u00b7\ ..." so Properties does not trim the space.
        assertEquals(" \u00b7 needs restart", NbBundle.getMessage(ACPOptionsPanel.class, "SCOPE_Restart"));
        assertEquals(" \u00b7 applies to new sessions",
                NbBundle.getMessage(ACPOptionsPanel.class, "SCOPE_NewSession"));
        assertEquals(" \u00b7 needs session reload",
                NbBundle.getMessage(ACPOptionsPanel.class, "SCOPE_SessionReload"));
    }

    private static int widthOf(String wrapped) {
        int start = wrapped.indexOf("width:") + "width:".length();
        int end = wrapped.indexOf("px'", start);
        return Integer.parseInt(wrapped.substring(start, end));
    }
}
