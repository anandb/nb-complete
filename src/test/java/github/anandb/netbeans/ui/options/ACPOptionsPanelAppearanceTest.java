package github.anandb.netbeans.ui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.Field;
import java.util.prefs.Preferences;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.awt.ColorComboBox;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.PreferenceKeys;
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
    private String chatLight;
    private String chatDark;
    private String fontFamily;

    @BeforeEach
    void setUp() {
        UiTestSupport.assumeGraphicsStack();
        userLight = PluginSettings.getUserBubbleBackgroundLight();
        userDark = PluginSettings.getUserBubbleBackgroundDark();
        assistantLight = PluginSettings.getAssistantBubbleBackgroundLight();
        assistantDark = PluginSettings.getAssistantBubbleBackgroundDark();
        chatLight = PluginSettings.getChatBackgroundLight();
        chatDark = PluginSettings.getChatBackgroundDark();
        fontFamily = PluginSettings.getChatFontFamily();
    }

    @AfterEach
    void tearDown() {
        PluginSettings.setUserBubbleBackgroundLight(userLight);
        PluginSettings.setUserBubbleBackgroundDark(userDark);
        PluginSettings.setAssistantBubbleBackgroundLight(assistantLight);
        PluginSettings.setAssistantBubbleBackgroundDark(assistantDark);
        PluginSettings.setChatBackgroundLight(chatLight);
        PluginSettings.setChatBackgroundDark(chatDark);
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
        PluginSettings.setChatBackgroundLight("#0d0e0f");
        PluginSettings.setChatBackgroundDark("#101112");
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
        assertEquals(new Color(0x0d, 0x0e, 0x0f),
                colorCombo(panel, "chatBackgroundLightCombo").getSelectedColor());
        assertEquals(new Color(0x10, 0x11, 0x12),
                colorCombo(panel, "chatBackgroundDarkCombo").getSelectedColor());
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
        colorCombo(panel, "chatBackgroundLightCombo").setSelectedColor(new Color(0x0d, 0x0e, 0x0f));
        colorCombo(panel, "chatBackgroundDarkCombo").setSelectedColor(new Color(0x10, 0x11, 0x12));
        // Pick whatever family this machine has installed: the combo is not editable, so a
        // named family that happens to be missing here would silently leave "Inherited" (the
        // empty override) selected and the test would fail for the wrong reason.
        JComboBox<String> fonts = fontCombo(panel);
        assumeTrue(fonts.getItemCount() > 1, "no installed font families to choose from");
        fonts.setSelectedIndex(1);
        String chosenFamily = (String) fonts.getSelectedItem();
        panel.store();

        assertEquals("#010203", PluginSettings.getUserBubbleBackgroundLight());
        assertEquals("#040506", PluginSettings.getUserBubbleBackgroundDark());
        assertEquals("#070809", PluginSettings.getAssistantBubbleBackgroundLight());
        assertEquals("#0a0b0c", PluginSettings.getAssistantBubbleBackgroundDark());
        assertEquals("#0d0e0f", PluginSettings.getChatBackgroundLight());
        assertEquals("#101112", PluginSettings.getChatBackgroundDark());
        assertEquals(chosenFamily, PluginSettings.getChatFontFamily());
    }

    @Test
    void appearanceOverridesLandOnTheirOwnPreferenceKeys() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();

        colorCombo(panel, "userBubbleLightCombo").setSelectedColor(new Color(0x01, 0x02, 0x03));
        colorCombo(panel, "userBubbleDarkCombo").setSelectedColor(new Color(0x04, 0x05, 0x06));
        colorCombo(panel, "assistantBubbleLightCombo").setSelectedColor(new Color(0x07, 0x08, 0x09));
        colorCombo(panel, "assistantBubbleDarkCombo").setSelectedColor(new Color(0x0a, 0x0b, 0x0c));
        colorCombo(panel, "chatBackgroundLightCombo").setSelectedColor(new Color(0x0d, 0x0e, 0x0f));
        colorCombo(panel, "chatBackgroundDarkCombo").setSelectedColor(new Color(0x10, 0x11, 0x12));
        panel.store();

        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        assertEquals("#010203", prefs.get(PreferenceKeys.USER_BUBBLE_LIGHT, ""));
        assertEquals("#040506", prefs.get(PreferenceKeys.USER_BUBBLE_DARK, ""));
        assertEquals("#070809", prefs.get(PreferenceKeys.ASSISTANT_BUBBLE_LIGHT, ""));
        assertEquals("#0a0b0c", prefs.get(PreferenceKeys.ASSISTANT_BUBBLE_DARK, ""));
        assertEquals("#0d0e0f", prefs.get(PreferenceKeys.CHAT_BACKGROUND_LIGHT, ""));
        assertEquals("#101112", prefs.get(PreferenceKeys.CHAT_BACKGROUND_DARK, ""));
    }

    @Test
    void choosingTheDefaultEntryClearsTheOverride() throws Exception {
        PluginSettings.setUserBubbleBackgroundLight("#112233");
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();

        PluginSettings.setChatBackgroundDark("#101112");
        colorCombo(panel, "userBubbleLightCombo")
                .setSelectedColor(ColorTheme.configuredBubbleColor(true, false));
        colorCombo(panel, "chatBackgroundDarkCombo")
                .setSelectedColor(ColorTheme.configuredChatBackground(true));
        panel.store();

        assertEquals("", PluginSettings.getUserBubbleBackgroundLight(),
                "the Default entry means no override, not a stored value");
        assertEquals("", PluginSettings.getChatBackgroundDark(),
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
        assertEquals(ColorTheme.configuredChatBackground(false),
                colorCombo(panel, "chatBackgroundLightCombo").getSelectedColor());
        assertEquals(ColorTheme.configuredChatBackground(true),
                colorCombo(panel, "chatBackgroundDarkCombo").getSelectedColor());
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
        assertSame(colorCombo(panel, "chatBackgroundLightCombo").getParent(),
                colorCombo(panel, "chatBackgroundDarkCombo").getParent(),
                "the light and dark pickers belong to the same row");
    }

    @Test
    void fieldsBelongToTheAppearancePage() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        for (String name : new String[]{"userBubbleLightCombo", "userBubbleDarkCombo",
                "assistantBubbleLightCombo", "assistantBubbleDarkCombo",
                "chatBackgroundLightCombo", "chatBackgroundDarkCombo", "chatFontFamilyCombo"}) {
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
