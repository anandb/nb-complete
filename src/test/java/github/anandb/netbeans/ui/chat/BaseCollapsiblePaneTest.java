package github.anandb.netbeans.ui.chat;

import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseCollapsiblePaneTest {

    @Test
    void testToggleCollapse() {
        BaseCollapsiblePane pane = new BaseCollapsiblePane(10, "Title", null, true) {

            @Override
            public void setTitle(String title) {}

            @Override
            public void setContent(String content) {}

            @Override
            public void appendContent(String text) {}

            @Override
            protected String getContentToCopy() {
                return "";
            }

            @Override
            protected Icon getDefaultIcon() {
                return null;
            }

            @Override
            protected void updateAppearance() {
                super.updateBaseAppearance();
            }
        };
        assertTrue(pane.isExpanded());

        pane.setExpanded(false);
        assertFalse(pane.isExpanded());

        pane.toggle();
        assertTrue(pane.isExpanded());
    }

    private static final String LONG_PATH =
            "edit /home/anand/workspace/nb-complete/src/main/java/github/anandb/netbeans/"
            + "ui/chat/MessageBubble.java";

    @Test
    void aNarrowHeaderElidesThePathButKeepsTheFileName() throws Exception {
        String shown = laidOutParam(LONG_PATH, 700);

        assertTrue(shown.contains("\u2026"), "the title must be elided, not clipped: " + shown);
        assertTrue(shown.endsWith("MessageBubble.java"), "the file name must survive: " + shown);
        assertFalse(shown.startsWith("/home/anand"), "the directory head is what goes: " + shown);
    }

    /**
     * A long parameter must never wrap onto a second title row: the row keeps the height of one
     * line, so a wrapped label is clipped to a sliver just above the card border.
     */
    @Test
    void aNarrowHeaderKeepsTheParameterOnTheTitleRow() throws Exception {
        BaseCollapsiblePane[] panes = new BaseCollapsiblePane[1];
        String[] laidOut = new String[2];
        SwingUtilities.invokeAndWait(() -> {
            CollapsibleToolPane pane = new CollapsibleToolPane(LONG_PATH, false);
            JPanel host = new JPanel(null);
            host.add(pane);
            host.setSize(700, 160);
            pane.setBounds(0, 0, 700, 160);
            pane.doLayout();
            pane.header.doLayout();
            pane.titlePanel.doLayout();
            panes[0] = pane;
            laidOut[0] = pane.paramLabel.getText();
            laidOut[1] = pane.titlePanel.getSize().width + "x" + pane.titlePanel.getSize().height;
        });
        BaseCollapsiblePane pane = panes[0];

        assertTrue(laidOut[0].contains("\u2026"), "the parameter is elided: " + laidOut[0]);
        assertEquals(pane.headerLabel.getY(), pane.paramLabel.getY(),
                "the parameter shares the title's row, not a second clipped one");
        assertTrue(pane.paramLabel.getY() + pane.paramLabel.getHeight() <= pane.titlePanel.getHeight(),
                "the parameter fits the title row (" + laidOut[1] + "): y=" + pane.paramLabel.getY()
                        + " h=" + pane.paramLabel.getHeight());
    }

    @Test
    void aWideHeaderShowsTheWholePath() throws Exception {
        String shown = laidOutParam(LONG_PATH, 4000);

        assertEquals(" " + LONG_PATH.substring("edit ".length()), shown,
                "with room for it, nothing is abbreviated");
    }

    @Test
    void theSegmentTitleFitsItsWidthToo() {
        BaseCollapsiblePane.ElidingHeaderLabel label =
                new BaseCollapsiblePane.ElidingHeaderLabel(LONG_PATH, null);
        label.setSize(220, 30);
        label.fitText();

        assertTrue(label.getText().contains("\u2026"), label.getText());
        assertTrue(label.getText().endsWith("MessageBubble.java"), label.getText());
        assertEquals(LONG_PATH, label.getToolTipText(), "the full title stays reachable");

        label.setSize(4000, 30);
        label.fitText();
        assertEquals(LONG_PATH, label.getText(), "a wide header shows the whole title");
        assertEquals(null, label.getToolTipText());
    }

    /** Lays a tool pane out at {@code width} and returns the parameter label's text. */
    private static String laidOutParam(String title, int width) throws Exception {
        String[] out = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            CollapsibleToolPane pane = new CollapsibleToolPane(title, false);
            JPanel host = new JPanel(null);
            host.add(pane);
            pane.setBounds(0, 0, width, 160);
            host.setSize(width, 160);
            pane.doLayout();
            pane.header.doLayout();
            pane.titlePanel.doLayout();
            out[0] = pane.paramLabel.getText();
        });
        return out[0];
    }
}
