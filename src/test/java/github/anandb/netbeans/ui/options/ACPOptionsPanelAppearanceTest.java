package github.anandb.netbeans.ui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.Field;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.awt.ColorComboBox;
import org.openide.util.NbBundle;

import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.UiTestSupport;
import github.anandb.netbeans.ui.rendering.ColorTheme;

/**
 * The five appearance overrides replaced system properties, so the panel must show the stored
 * values and write edits back. Colours use NetBeans' own {@link ColorComboBox}, whose first entry
 * is the theme colour (no override) and whose last entry opens the colour chooser.
 */
class ACPOptionsPanelAppearanceTest {

    private String userLight;
    private String userDark;
    private String assistantLight;
    private String assistantDark;
    private String fontFamily;

    @BeforeEach
    void setUp() {
        UiTestSupport.assumeGraphicsStack();
        userLight = PluginSettings.getUserBubbleBackgroundLight();
        userDark = PluginSettings.getUserBubbleBackgroundDark();
        assistantLight = PluginSettings.getAssistantBubbleBackgroundLight();
        assistantDark = PluginSettings.getAssistantBubbleBackgroundDark();
        fontFamily = PluginSettings.getChatFontFamily();
    }

    @AfterEach
    void tearDown() {
        PluginSettings.setUserBubbleBackgroundLight(userLight);
        PluginSettings.setUserBubbleBackgroundDark(userDark);
        PluginSettings.setAssistantBubbleBackgroundLight(assistantLight);
        PluginSettings.setAssistantBubbleBackgroundDark(assistantDark);
        PluginSettings.setChatFontFamily(fontFamily);
        // The setters do not reach the UI caches, so a later test would see this font/colour.
        github.anandb.netbeans.ui.rendering.ThemeManager.clearFontCache();
        github.anandb.netbeans.ui.rendering.ThemeManager.reloadTheme();
        github.anandb.netbeans.ui.components.UIUtils.clearFontStackCache();
    }

    @Test
    void loadShowsTheStoredOverrides() throws Exception {
        PluginSettings.setUserBubbleBackgroundLight("#112233");
        PluginSettings.setUserBubbleBackgroundDark("#445566");
        PluginSettings.setAssistantBubbleBackgroundLight("#778899");
        PluginSettings.setAssistantBubbleBackgroundDark("#aabbcc");
        PluginSettings.setChatFontFamily("Segoe UI");

        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();

        assertEquals(new Color(0x11, 0x22, 0x33),
                colorCombo(panel, "userBubbleLightCombo").getSelectedColor());
        assertEquals(new Color(0x44, 0x55, 0x66),
                colorCombo(panel, "userBubbleDarkCombo").getSelectedColor());
        assertEquals(new Color(0x77, 0x88, 0x99),
                colorCombo(panel, "assistantBubbleLightCombo").getSelectedColor());
        assertEquals(new Color(0xaa, 0xbb, 0xcc),
                colorCombo(panel, "assistantBubbleDarkCombo").getSelectedColor());
        assertEquals("Segoe UI", fontCombo(panel).getSelectedItem(),
                "a stored family that is not installed must stay selected");
    }

    @Test
    void storePersistsEveryField() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();

        colorCombo(panel, "userBubbleLightCombo").setSelectedColor(new Color(0x01, 0x02, 0x03));
        colorCombo(panel, "userBubbleDarkCombo").setSelectedColor(new Color(0x04, 0x05, 0x06));
        colorCombo(panel, "assistantBubbleLightCombo").setSelectedColor(new Color(0x07, 0x08, 0x09));
        colorCombo(panel, "assistantBubbleDarkCombo").setSelectedColor(new Color(0x0a, 0x0b, 0x0c));
        fontCombo(panel).setSelectedItem("Fira Sans");
        panel.store();

        assertEquals("#010203", PluginSettings.getUserBubbleBackgroundLight());
        assertEquals("#040506", PluginSettings.getUserBubbleBackgroundDark());
        assertEquals("#070809", PluginSettings.getAssistantBubbleBackgroundLight());
        assertEquals("#0a0b0c", PluginSettings.getAssistantBubbleBackgroundDark());
        assertEquals("Fira Sans", PluginSettings.getChatFontFamily());
    }

    @Test
    void choosingTheDefaultEntryClearsTheOverride() throws Exception {
        PluginSettings.setUserBubbleBackgroundLight("#112233");
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();

        colorCombo(panel, "userBubbleLightCombo")
                .setSelectedColor(ColorTheme.configuredBubbleColor(true, false));
        panel.store();

        assertEquals("", PluginSettings.getUserBubbleBackgroundLight(),
                "the Default entry means no override, not a stored value");
    }

    @Test
    void pickersStartAtTheConfiguredColours() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());

        assertEquals(ColorTheme.configuredBubbleColor(true, false),
                colorCombo(panel, "userBubbleLightCombo").getSelectedColor());
        assertEquals(ColorTheme.configuredBubbleColor(true, true),
                colorCombo(panel, "userBubbleDarkCombo").getSelectedColor());
        assertEquals(ColorTheme.configuredBubbleColor(false, false),
                colorCombo(panel, "assistantBubbleLightCombo").getSelectedColor());
        assertEquals(ColorTheme.configuredBubbleColor(false, true),
                colorCombo(panel, "assistantBubbleDarkCombo").getSelectedColor());
        assertNotEquals(ColorTheme.configuredBubbleColor(true, false),
                ColorTheme.configuredBubbleColor(true, true),
                "the light and dark defaults are distinct colours");
    }

    @Test
    void pickerHasADefaultAndACustomEntry() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        ColorComboBox combo = colorCombo(panel, "userBubbleLightCombo");

        assertFalse(combo.isEditable(), "values are picked, not typed");
        assertEquals(2, combo.getItemCount(), "the Default entry plus the Custom entry");
        assertEquals(NbBundle.getMessage(UIBundle.class, "LBL_DefaultEntry"),
                combo.getItemAt(0).toString());
    }

    @Test
    void darkBubblesAreVisiblyDistinct() {
        Color user = ColorTheme.configuredBubbleColor(true, true);
        Color assistant = ColorTheme.configuredBubbleColor(false, true);

        // The two dark bubbles are adjacent large surfaces; at the same lightness they read
        // as one colour, which is what this guards against.
        double ratio = contrastRatio(user, assistant);
        assertTrue(ratio >= 1.15, "dark user/assistant bubbles need a visible step, was " + ratio);
    }

    private static double contrastRatio(Color a, Color b) {
        double first = relativeLuminance(a);
        double second = relativeLuminance(b);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double relativeLuminance(Color color) {
        double r = linearChannel(color.getRed() / 255.0);
        double g = linearChannel(color.getGreen() / 255.0);
        double b = linearChannel(color.getBlue() / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearChannel(double value) {
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    @Test
    void lightAndDarkShareOneRow() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        assertSame(colorCombo(panel, "userBubbleLightCombo").getParent(),
                colorCombo(panel, "userBubbleDarkCombo").getParent(),
                "the light and dark pickers belong to the same row");
        assertSame(colorCombo(panel, "assistantBubbleLightCombo").getParent(),
                colorCombo(panel, "assistantBubbleDarkCombo").getParent(),
                "the light and dark pickers belong to the same row");
    }

    @Test
    void fieldsBelongToTheAppearancePage() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        for (String name : new String[]{"userBubbleLightCombo", "userBubbleDarkCombo",
                "assistantBubbleLightCombo", "assistantBubbleDarkCombo", "chatFontFamilyCombo"}) {
            assertTrue(SwingUtilities.isDescendingFrom(component(panel, name), panel),
                    name + " must be part of the panel");
        }
    }

    private static ColorComboBox colorCombo(ACPOptionsPanel panel, String name) throws Exception {
        return (ColorComboBox) component(panel, name);
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<String> fontCombo(ACPOptionsPanel panel) throws Exception {
        return (JComboBox<String>) component(panel, "chatFontFamilyCombo");
    }

    private static Component component(ACPOptionsPanel panel, String name) throws Exception {
        Field field = ACPOptionsPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Component) field.get(panel);
    }
}
