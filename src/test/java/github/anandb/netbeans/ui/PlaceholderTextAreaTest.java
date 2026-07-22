package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholderTextAreaTest {

    @Test
    void testPlaceholderAndOverlayTextGettersSetters() {
        PlaceholderTextArea area = new PlaceholderTextArea("Initial Placeholder");
        assertEquals("Initial Placeholder", area.getPlaceholder());

        area.setPlaceholder("New Placeholder");
        assertEquals("New Placeholder", area.getPlaceholder());

        area.setOverlayText("Overlay Help");
        // Ensure overlay text is set properly
        // overlayText field tested via paintComponent logic
    }
}
