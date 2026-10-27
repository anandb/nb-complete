package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The button band sits at the bottom of a {@code BorderLayout}, so the height it asks
 * for is the only space it gets: a row that reports one row of height but lays out
 * five is clipped, which is how Devin's Reject button became invisible. Swing only
 * builds these components headless, so no frame is involved.
 */
class ButtonRowLayoutTest {

    private static final int GAP = 8;

    private static final String[] DEVIN_LABELS = {
        "Allow",
        "Yes, allow `git reset` commands (this session)",
        "Yes, always allow `git reset` commands in `bridge`",
        "Yes, always allow `git reset` commands in all projects",
        "Yes, switch to bypass mode",
        "Reject",
    };

    private static JButton button(String text) {
        return new JButton(text);
    }

    private static JButton[] devinButtons() {
        JButton[] buttons = new JButton[DEVIN_LABELS.length];
        for (int i = 0; i < DEVIN_LABELS.length; i++) {
            buttons[i] = button(DEVIN_LABELS[i]);
        }
        return buttons;
    }

    /**
     * Sizes a host panel the way the sidebar does and lays it out. The row is the
     * SOUTH child, so it is allocated exactly the preferred height it reports.
     */
    private static JPanel rowAt(int width, Component... children) {
        JPanel row = new JPanel(new ButtonRowLayout(8, 4));
        for (Component child : children) {
            row.add(child);
        }
        JPanel host = new JPanel(new BorderLayout());
        host.add(new JPanel(), BorderLayout.CENTER);
        host.add(row, BorderLayout.SOUTH);
        host.setSize(new Dimension(width, 600));
        layOut(host);
        return row;
    }

    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layOut((Container) child);
            }
        }
    }

    /** Distinct row tops among the visible buttons of a laid-out band. */
    private static Set<Integer> rowTops(JPanel row) {
        Set<Integer> tops = new LinkedHashSet<>();
        for (Component child : row.getComponents()) {
            if (child.isVisible()) {
                tops.add(child.getY());
            }
        }
        return tops;
    }

    private static int drawnHeight(JPanel row) {
        int bottom = 0;
        for (Component child : row.getComponents()) {
            if (child.isVisible()) {
                bottom = Math.max(bottom, child.getY() + child.getHeight());
            }
        }
        return bottom;
    }

    private static String describe(JPanel row) {
        StringBuilder sb = new StringBuilder("rows=").append(rowTops(row))
                .append(" height=").append(row.getHeight())
                .append(" drawn=").append(drawnHeight(row)).append('\n');
        for (Component child : row.getComponents()) {
            sb.append("  ").append(((JButton) child).getText()).append(' ').append(child.getBounds()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Width that lets the six labels sit three to a row for the current LAF font.
     * Hardcoding pixels here would pass on the desktop theme and fail on Metal.
     */
    private static int twoRowWidth(JButton[] buttons) {
        int first = 0;
        int second = 0;
        for (int i = 0; i < buttons.length; i++) {
            if (i < 3) {
                first += buttons[i].getPreferredSize().width;
            } else {
                second += buttons[i].getPreferredSize().width;
            }
        }
        return Math.max(first, second) + GAP * 4;
    }

    /** Width that fits every button on one row, for the current LAF font. */
    private static int oneRowWidth(JButton[] buttons) {
        int total = 0;
        for (JButton button : buttons) {
            total += button.getPreferredSize().width;
        }
        return total + GAP * (buttons.length + 1);
    }

    private static JButton[] shortAnswers() {
        return new JButton[] {
            button("Allow once"),
            button("Always allow"),
            button("Reject"),
            button("Always reject"),
        };
    }

    @Test
    void fourShortAnswersShareOneRowWhenTheyFit() {
        JButton[] buttons = shortAnswers();
        JPanel row = rowAt(oneRowWidth(buttons), buttons);
        assertEquals(1, rowTops(row).size(), "a row breaks only when the width runs out\n" + describe(row));
        assertEquals(drawnHeight(row), row.getHeight(), describe(row));
    }

    @Test
    void aFourthAnswerWrapsOnlyWhenItCannotFit() {
        JButton[] buttons = shortAnswers();
        int threeButtons = 0;
        for (int i = 0; i < 3; i++) {
            threeButtons += buttons[i].getPreferredSize().width;
        }
        JPanel row = rowAt(threeButtons + GAP * 3, buttons);
        assertEquals(2, rowTops(row).size(), describe(row));
        assertEquals(drawnHeight(row), row.getHeight(), describe(row));
    }

    @Test
    void devinSixOptionsFitTwoRowsOnAWideSidebar() {
        JButton[] buttons = devinButtons();
        JPanel row = rowAt(twoRowWidth(buttons), buttons);
        assertEquals(2, rowTops(row).size(), describe(row));
        assertEquals(drawnHeight(row), row.getHeight(), "band must reserve what it draws\n" + describe(row));
    }

    @Test
    void narrowSidebarGrowsInsteadOfClipping() {
        JButton[] buttons = devinButtons();
        JPanel row = rowAt(twoRowWidth(buttons) / 2, buttons);
        assertTrue(rowTops(row).size() > 2, describe(row));
        assertEquals(drawnHeight(row), row.getHeight(), "band must reserve what it draws\n" + describe(row));
        for (Component child : row.getComponents()) {
            assertTrue(child.getX() >= 0, "button pushed outside the band\n" + describe(row));
            assertTrue(child.getY() + child.getHeight() <= row.getHeight(), describe(row));
        }
    }

    @Test
    void narrowingTheSidebarReReservesTheHeight() {
        JButton[] buttons = devinButtons();
        int wide = twoRowWidth(buttons);
        JPanel row = rowAt(wide, buttons);
        int wideHeight = row.getHeight();
        assertEquals(2, rowTops(row).size(), describe(row));

        row.getParent().setSize(new Dimension(wide / 2, 600));
        layOut(row.getParent());

        assertEquals(drawnHeight(row), row.getHeight(), "band must reserve what it draws\n" + describe(row));
        assertTrue(row.getHeight() > wideHeight, "narrowing must add rows\n" + describe(row));
    }

    /**
     * pack() can measure the band while no ancestor has a width, reserving one row
     * of height; the band then lays out more rows into that space. The first
     * layout at a real width must ask the host for one more pass, and a repeat
     * layout at the same width must not ask again (no revalidate loop).
     */
    @Test
    void firstLayoutThatNoLongerFitsRequestsOneRelayout() throws Exception {
        JButton[] buttons = devinButtons();
        JPanel row = new JPanel(new ButtonRowLayout(8, 4));
        for (JButton button : buttons) {
            row.add(button);
        }
        int[] revalidations = new int[1];
        JPanel host = new JPanel(new BorderLayout()) {
            @Override
            public void revalidate() {
                revalidations[0]++;
                super.revalidate();
            }
        };
        host.add(row, BorderLayout.SOUTH);

        int staleHeight = row.getPreferredSize().height; // measured with no width
        int narrow = twoRowWidth(buttons) / 2;
        host.setSize(new Dimension(narrow, 600));
        row.setBounds(0, 0, narrow, staleHeight);
        // Resizing the child makes Swing propagate its own invalidations; count
        // only what the band itself requests from here on.
        SwingUtilities.invokeAndWait(() -> { });
        int baseline = revalidations[0];

        row.doLayout();
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(baseline + 1, revalidations[0], "one relayout request when the rows no longer fit");

        row.doLayout();
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(baseline + 1, revalidations[0], "the same width must not queue another pass");
    }

    @Test
    void aWideBandKeepsEveryButtonOnOneRow() {
        JButton[] buttons = devinButtons();
        JPanel row = rowAt(4000, buttons);
        assertEquals(1, rowTops(row).size(), "width alone decides the break\n" + describe(row));
        assertEquals(drawnHeight(row), row.getHeight(), describe(row));
    }

    @Test
    void buttonsAreRightAligned() {
        JButton[] buttons = devinButtons();
        JPanel row = rowAt(twoRowWidth(buttons), buttons);
        int rightmost = 0;
        for (Component child : row.getComponents()) {
            rightmost = Math.max(rightmost, child.getX() + child.getWidth());
        }
        assertEquals(row.getWidth(), rightmost, describe(row));
    }

    @Test
    void hiddenButtonsDoNotConsumeASlot() {
        JButton[] buttons = shortAnswers();
        JButton hidden = button("hidden");
        hidden.setVisible(false);
        JPanel row = rowAt(oneRowWidth(buttons), hidden, buttons[0], buttons[1], buttons[2], buttons[3]);
        assertEquals(1, rowTops(row).size(), describe(row));
        assertEquals(drawnHeight(row), row.getHeight(), describe(row));
    }
}
