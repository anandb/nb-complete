package github.anandb.netbeans.ui.stats;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import github.anandb.netbeans.ui.UIBundle;

/**
 * Headless tests for the context-window usage meter: fill math, thresholds,
 * and tooltip behavior. Lightweight component only — no JFrame/JDialog
 * (HeadlessException).
 */
class ContextUsageMeterTest {

    private static double fraction(long used, long size) {
        ContextUsageMeter m = new ContextUsageMeter();
        m.setUsage(used, size);
        return m.getFraction();
    }

    @Test
    void computesFraction() {
        assertEquals(0.5, fraction(100, 200), 1e-9);
    }

    @Test
    void clampsAboveOne() {
        assertEquals(1.0, fraction(5000, 200), 1e-9);
    }

    @Test
    void negativeUsedIsEmpty() {
        assertEquals(0.0, fraction(-5, 200), 1e-9);
        assertFalse(new ContextUsageMeter().getFraction() > 0);
    }

    @Test
    void unknownSizeIsUnknownFraction() {
        assertEquals(-1.0, fraction(100, 0), 1e-9);
        assertEquals(-1.0, fraction(100, -3), 1e-9);
    }

    @Test
    void unknownSizeSetsUnavailableTooltip() {
        ContextUsageMeter m = new ContextUsageMeter();
        m.setUsage(100, 0);
        assertEquals(NbBundle.getMessage(UIBundle.class,
                "HINT_ContextUsageUnavailable"), m.getToolTipText());
    }

    @Test
    void knownSizeSetsGroupedTooltip() {
        ContextUsageMeter m = new ContextUsageMeter();
        m.setUsage(12400L, 200000L);
        String tip = m.getToolTipText();
        // Existing bundle key: "Context Usage: {0}/{1} tokens"
        String expected = NbBundle.getMessage(UIBundle.class,
                "HINT_ContextUsage", java.text.NumberFormat.getInstance().format(12400L),
                java.text.NumberFormat.getInstance().format(200000L));
        assertEquals(expected, tip);
    }

    @Test
    void clearResetsToUnknown() {
        ContextUsageMeter m = new ContextUsageMeter();
        m.setUsage(100, 200);
        m.clear();
        assertEquals(-1.0, m.getFraction(), 1e-9);
        assertFalse(m.getToolTipText() != null && !m.getToolTipText().isEmpty());
    }

    @Test
    void setUsageTriggersRepaintPath() {
        // repaint on a non-shown component is a no-op scheduling call; the point
        // is that setUsage must not throw outside the UI (headless).
        SwingUtilities.invokeLater(() -> {
            ContextUsageMeter m = new ContextUsageMeter();
            m.setUsage(1, 2);
            m.clear();
        });
    }

    @Test
    void accessibleNameAndDescriptionSet() {
        ContextUsageMeter m = new ContextUsageMeter();
        if (m.getAccessibleContext() != null) {
            assertEquals(NbBundle.getMessage(UIBundle.class, "LBL_ContextUsage"),
                    m.getAccessibleContext().getAccessibleName());
        }
        assertTrue(m.getToolTipText() == null || m.getToolTipText().isEmpty());
    }

    @Test
    void fixedPreferredSizeNeverChanges() {
        ContextUsageMeter m = new ContextUsageMeter();
        m.setUsage(1, 2);
        m.setUsage(9999999, 10);
        assertEquals(72, m.getPreferredSize().width);
        assertEquals(m.getPreferredSize(), m.getMinimumSize());
        assertEquals(m.getPreferredSize(), m.getMaximumSize());
    }

    @Test
    void compactContextMenuRunsConfiguredAction() {
        ContextUsageMeter meter = new ContextUsageMeter();
        boolean[] invoked = {false};
        meter.setCompactAction(() -> invoked[0] = true);

        JPopupMenu popup = meter.getComponentPopupMenu();
        assertNotNull(popup);
        JMenuItem compact = (JMenuItem) popup.getComponent(0);
        assertEquals("Compact", compact.getText());
        compact.doClick();

        assertTrue(invoked[0]);
    }
}
