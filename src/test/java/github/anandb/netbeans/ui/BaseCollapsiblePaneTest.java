package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaseCollapsiblePaneTest {

    @Test
    void testToggleCollapse() {
        BaseCollapsiblePane pane = new BaseCollapsiblePane(10, "Title", null, true) {};
        assertTrue(pane.isExpanded());
        
        pane.setExpanded(false);
        assertFalse(pane.isExpanded());
        
        pane.toggle();
        assertTrue(pane.isExpanded());
    }
}
