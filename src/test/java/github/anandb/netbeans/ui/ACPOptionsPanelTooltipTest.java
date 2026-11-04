package github.anandb.netbeans.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.lang.reflect.Field;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

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
    void scopeMarkersStayCompactAndKeepTheirLeadingSpace() {
        // Escaped in Bundle.properties as "\ \u00b9" so Properties does not trim the space.
        assertMarker("SCOPE_Restart", '\u00b9');
        assertMarker("SCOPE_SessionReload", '\u00b2');
        assertMarker("SCOPE_NewSession", '\u00b3');
    }

    @Test
    void scopeLegendExplainsEveryMarker() {
        String legend = NbBundle.getMessage(ACPOptionsPanel.class, "SCOPE_Legend");

        assertTrue(legend.contains("\u00b9"), legend);
        assertTrue(legend.contains("\u00b2"), legend);
        assertTrue(legend.contains("\u00b3"), legend);
    }

    private static void assertMarker(String key, char glyph) {
        String marker = NbBundle.getMessage(ACPOptionsPanel.class, key);
        assertEquals(" " + glyph, marker);
        assertTrue(marker.length() <= 2, "marker must stay a single glyph, was: " + marker);
    }

    @Test
    void panelRendersCompactMarkersWithOneLegend() throws Exception {
        UiTestSupport.assumeGraphicsStack();

        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        JCheckBox mcp = (JCheckBox) field(panel, "mcpServerCheckbox");

        assertEquals(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EnableMcpServers") + " \u00b9",
                mcp.getText());
        assertNotNull(findLabel(panel, NbBundle.getMessage(ACPOptionsPanel.class, "SCOPE_Legend")),
                "the marker legend must be visible at the top of the panel");
        assertEquals(Component.CENTER_ALIGNMENT,
                findLabel(panel, NbBundle.getMessage(ACPOptionsPanel.class, "SCOPE_Legend")).getAlignmentX(),
                "the legend must be centred, not lost among the left-aligned rows");
    }

    @Test
    void switchHintStartsWithTheCheckboxLabelText() throws Exception {
        UiTestSupport.assumeGraphicsStack();

        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        JCheckBox check = (JCheckBox) field(panel, "projectSwitchRestartCheckbox");
        Container system = check.getParent();
        JPanel hint = (JPanel) field(panel, "projectSwitchHint");
        assertEquals(2, hint.getComponentCount(), "the restart explanation must read on two lines");

        Dimension preferred = system.getPreferredSize();
        system.setSize(preferred.width, preferred.height);
        system.doLayout();

        int textX = check.getX() + ACPOptionsPanel.checkboxTextInset(check);
        assertTrue(ACPOptionsPanel.checkboxTextInset(check) > check.getInsets().left,
                "the LAF glyph must be counted even when the component icon is null");
        assertEquals(textX, hint.getX(), 1,
                "the hint must line up with the checkbox text, not the check glyph");
        assertTrue(hint.getY() - (check.getY() + check.getHeight()) <= 2,
                "the hint must sit directly under the checkbox, gap was "
                        + (hint.getY() - (check.getY() + check.getHeight())) + "px");

        JButton whitelist = (JButton) field(panel, "runCommandPolicyButton");
        assertEquals(hint.getY() + 4, whitelist.getY(),
                "the two-line hint must not push the whitelist button down");
    }

    @Test
    void systemCheckboxRowsAreEvenlySpaced() throws Exception {
        UiTestSupport.assumeGraphicsStack();

        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        JCheckBox first = (JCheckBox) field(panel, "checkForUpdatesCheckbox");
        JCheckBox second = (JCheckBox) field(panel, "mcpServerCheckbox");
        JCheckBox third = (JCheckBox) field(panel, "projectSwitchRestartCheckbox");

        Container system = first.getParent();
        Dimension preferred = system.getPreferredSize();
        system.setSize(preferred.width, preferred.height);
        system.doLayout();

        assertEquals(second.getY() - first.getY(), third.getY() - second.getY(), 1,
                "the third checkbox must not inherit the taller grid row above it");
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
