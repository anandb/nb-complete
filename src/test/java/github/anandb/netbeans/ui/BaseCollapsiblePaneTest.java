package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;

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
            protected javax.swing.Icon getDefaultIcon() {
                return null;
            }
        };
        assertTrue(pane.isExpanded());

        pane.setExpanded(false);
        assertFalse(pane.isExpanded());

        pane.toggle();
        assertTrue(pane.isExpanded());
    }
}
