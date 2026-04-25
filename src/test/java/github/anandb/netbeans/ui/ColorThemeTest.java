package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import javax.swing.UIManager;
import static org.junit.jupiter.api.Assertions.*;

class ColorThemeTest {

    @Test
    void testColorThemeGetters() {
        // Initialize UIManager defaults for the test environment
        UIManager.put("Panel.background", Color.BLACK);
        UIManager.put("Panel.foreground", Color.WHITE);
        UIManager.put("TextArea.selectionBackground", Color.BLUE);
        UIManager.put("Button.focusColor", Color.CYAN);
        UIManager.put("Label.background", Color.GRAY);
        UIManager.put("TextField.background", Color.LIGHT_GRAY);
        UIManager.put("TextArea.foreground", Color.WHITE);
        UIManager.put("Button.borderColor", Color.DARK_GRAY);
        UIManager.put("Button.background", Color.GRAY);
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("controlShadow", Color.BLACK);
        UIManager.put("control", Color.GRAY);
        UIManager.put("ComboBox.selectionBackground", Color.YELLOW);
        UIManager.put("OptionPane.background", Color.BLACK);
        UIManager.put("OptionPane.messageForeground", Color.WHITE);

        ColorTheme theme = ThemeManager.getCurrentTheme();
        assertNotNull(theme.background());
        assertNotNull(theme.foreground());
        assertNotNull(theme.bubbleUser());
        assertNotNull(theme.assistantForeground());
    }
}
