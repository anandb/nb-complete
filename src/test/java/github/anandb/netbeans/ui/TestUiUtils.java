package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

public final class TestUiUtils {
    private TestUiUtils() {}

    public static void setupTestUIManager() {
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
        UIManager.put("TabbedPane.unselectedBackground", Color.GRAY);
        UIManager.put("Label.font", new Font("Dialog", Font.PLAIN, 12));
    }
}
