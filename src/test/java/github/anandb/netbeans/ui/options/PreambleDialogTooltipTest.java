package github.anandb.netbeans.ui.options;

import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.TestUiUtils;

/**
 * The copy-button tooltip carries its structure in the bundle text: one paragraph per
 * line, lead sentence bold. Nothing may depend on character counts to decide breaks.
 */
class PreambleDialogTooltipTest {

    @Test
    void leadParagraphIsBoldAndFollowingLinesAreSeparated() {
        String html = PreambleDialog.copyTooltipHtml("Lead sentence.\nStep one.\nStep two.");

        assertTrue(html.startsWith("<html><body style='width: "), html);
        assertTrue(html.endsWith("</body></html>"), html);
        assertTrue(html.contains("<b>Lead sentence.</b>"), html);
        assertEquals(2, occurrences(html, "<br><br>"), html);
        assertTrue(html.contains("<b>Lead sentence.</b><br><br>Step one.<br><br>Step two."), html);
    }

    @Test
    void blankLinesAndSurroundingSpacesDoNotAddParagraphs() {
        assertEquals(PreambleDialog.copyTooltipHtml("First\nSecond"),
                PreambleDialog.copyTooltipHtml(" First \n\n  Second  \n"));
    }

    @Test
    void missingTextYieldsAnEmptyTooltip() {
        assertEquals(PreambleDialog.copyTooltipHtml(""), PreambleDialog.copyTooltipHtml(null));
        assertTrue(PreambleDialog.copyTooltipHtml(null).endsWith("</body></html>"));
    }

    @Test
    void bundleTextRendersLeadPlusTwoFollowUpParagraphs() {
        String text = NbBundle.getMessage(UIBundle.class, "TT_PreambleCopySystem");

        String html = PreambleDialog.copyTooltipHtml(text);

        assertEquals(2, occurrences(html, "<br><br>"), html);
        assertTrue(html.contains("<b>Copies the full system prompt"), html);
        assertTrue(html.contains("Paste it into the agent's own configuration"), html);
        assertTrue(html.contains("Then uncheck Enable Session Preamble"), html);
    }

    @Test
    void renderedTooltipWrapsInsideTheGivenWidth() {
        TestUiUtils.setupTestUIManager();
        String text = NbBundle.getMessage(UIBundle.class, "TT_PreambleCopySystem");

        javax.swing.JLabel tip = new javax.swing.JLabel(PreambleDialog.copyTooltipHtml(text));
        java.awt.Dimension preferred = tip.getPreferredSize();

        assertTrue(preferred.width <= 440, "tooltip wider than its wrap box: " + preferred.width);
        assertTrue(preferred.height > 60, "three paragraphs should not fit one line: " + preferred.height);
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
