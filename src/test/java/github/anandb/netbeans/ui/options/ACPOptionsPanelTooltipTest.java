package github.anandb.netbeans.ui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;

import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.UiTestSupport;

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

        assertTrue(wrapped.contains("&lt;b"), wrapped);
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
    void scopeMarkersStayCompactAndKeepTheirLeadingSpace() {
        // Escaped in Bundle.properties as "\ \u00b9" so Properties does not trim the space.
        assertMarker("SCOPE_Restart", '\u00b9');
        assertMarker("SCOPE_SessionReload", '\u00b2');
        assertMarker("SCOPE_NewSession", '\u00b3');
    }

    @Test
    void scopeLegendExplainsEveryMarker() {
        String legend = NbBundle.getMessage(UIBundle.class, "SCOPE_Legend");

        assertTrue(legend.contains("\u00b9"), legend);
        assertTrue(legend.contains("\u00b2"), legend);
        assertTrue(legend.contains("\u00b3"), legend);
    }

    private static void assertMarker(String key, char glyph) {
        String marker = NbBundle.getMessage(UIBundle.class, key);
        assertEquals(" " + glyph, marker);
        assertTrue(marker.length() <= 2, "marker must stay a single glyph, was: " + marker);
    }

    @Test
    void panelRendersCompactMarkersWithOneLegend() throws Exception {
        UiTestSupport.assumeGraphicsStack();

        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        JCheckBox mcp = (JCheckBox) field(panel, "mcpServerCheckbox");

        assertEquals(NbBundle.getMessage(UIBundle.class, "LBL_EnableMcpServers") + " \u00b9",
                mcp.getText());
        assertNotNull(findLabel(panel, NbBundle.getMessage(UIBundle.class, "SCOPE_Legend")),
                "the marker legend must be visible at the top of the panel");
        assertEquals(Component.CENTER_ALIGNMENT,
                findLabel(panel, NbBundle.getMessage(UIBundle.class, "SCOPE_Legend")).getAlignmentX(),
                "the legend must be centred, not lost among the left-aligned rows");
    }

    private static Object field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }

    private static JLabel findLabel(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
        }
        return null;
    }

    private static int widthOf(String wrapped) {
        int start = wrapped.indexOf("width:") + "width:".length();
        int end = wrapped.indexOf("px'", start);
        return Integer.parseInt(wrapped.substring(start, end));
    }
}
