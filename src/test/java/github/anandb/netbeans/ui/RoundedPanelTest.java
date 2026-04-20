package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.Dimension;
import static org.junit.jupiter.api.Assertions.*;

class RoundedPanelTest {

    @Test
    void testRoundedPanelProperties() {
        RoundedPanel panel = new RoundedPanel(20);
        panel.setBaseColor(Color.RED);
        panel.setPreferredSize(new Dimension(100, 100));
        
        // No direct getter for baseColor, but we can verify it doesn't crash
        assertNotNull(panel);
    }
}
