package github.anandb.netbeans.ui.rendering;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.swing.UIManager;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.ColorKey;
import github.anandb.netbeans.model.ColorRegistry;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.PluginSettings;
import org.openide.util.NbBundle;
import github.anandb.netbeans.ui.UIBundle;

/**
 * Centralized color definitions for the ACP NetBeans plugin.
 * Wraps a {@link ColorRegistry} for type-safe color access while keeping the
 * same method-level API as the previous record — callers are unaffected.
 *
 * <p>Cached singleton refreshed on L&amp;F changes.
 */
public final class ColorTheme {

    private final boolean isDark;
    private final ColorRegistry registry;

    private ColorTheme(boolean isDark, ColorRegistry registry) {
        this.isDark = isDark;
        this.registry = registry;
    }

    // ---- factory & caching --------------------------------------------------

    private static volatile ColorTheme cachedTheme;

    static {
        UIManager.addPropertyChangeListener(e -> invalidate());
    }

    /** Drops the cached theme and every cache built from it; the next read reloads colors.json. */
    public static synchronized void invalidate() {
        cachedTheme = null;
        lightDefaults = null;
        darkDefaults = null;
        CssGenerator.clearGeneratedCss();
        CssGenerator.clearHexCache();
        CssGenerator.clearCssTemplateCache();
        StyleResolver.clearCache();
        HtmlContentPreparer.clearCache();
    }

    /** Returns the cached native theme, refreshing it if the L&amp;F changed. */
    public static synchronized ColorTheme getNativeTheme(boolean darkMode) {
        ColorTheme theme = cachedTheme;
        if (theme != null) {
            return theme;
        }
        ColorRegistry reg = ColorRegistry.fromJson(loadColorConfig(), darkMode, colorOverrides());
        theme = new ColorTheme(darkMode, reg);
        cachedTheme = theme;
        return theme;
    }

    /** Property names from colors.json that have an Options setting; a system property wins. */
    private static final String PROP_USER_BUBBLE_LIGHT = "beanbot.color.bubbleUserBackground";
    private static final String PROP_USER_BUBBLE_DARK = "beanbot.color.bubbleUserBackgroundDark";
    private static final String PROP_ASSISTANT_BUBBLE_LIGHT = "beanbot.color.bubbleAssistant";
    private static final String PROP_ASSISTANT_BUBBLE_DARK = "beanbot.color.bubbleAssistantDark";
    private static final String PROP_SUNKEN_LIGHT = "beanbot.color.sunkenBackgroundLight";
    private static final String PROP_SUNKEN_DARK = "beanbot.color.sunkenBackgroundDark";

    private static Map<String, String> colorOverrides() {
        Map<String, String> overrides = new HashMap<>();
        putOverride(overrides, PROP_USER_BUBBLE_LIGHT, PluginSettings.getUserBubbleBackgroundLight());
        putOverride(overrides, PROP_USER_BUBBLE_DARK, PluginSettings.getUserBubbleBackgroundDark());
        putOverride(overrides, PROP_ASSISTANT_BUBBLE_LIGHT,
                PluginSettings.getAssistantBubbleBackgroundLight());
        putOverride(overrides, PROP_ASSISTANT_BUBBLE_DARK,
                PluginSettings.getAssistantBubbleBackgroundDark());
        putOverride(overrides, PROP_SUNKEN_LIGHT, PluginSettings.getChatBackgroundLight());
        putOverride(overrides, PROP_SUNKEN_DARK, PluginSettings.getChatBackgroundDark());
        return overrides;
    }

    /** Blank means "not set", so the system property (then the theme) still applies. */
    private static void putOverride(Map<String, String> overrides, String property, String value) {
        if (value != null && !value.isBlank()) {
            overrides.put(property, value);
        }
    }

    /** Cached colours.json registries used to preview the configured bubble colours. */
    private static volatile ColorRegistry lightDefaults;
    private static volatile ColorRegistry darkDefaults;
    private static final Object DEFAULTS_LOCK = new Object();

    /**
     * The bubble background {@code colors.json} configures for the given theme, ignoring the
     * Options overrides (a system property still applies). Used to seed the Appearance swatches
     * with the colour that takes effect when no override is stored.
     */
    public static Color configuredBubbleColor(boolean userBubble, boolean darkMode) {
        Color color = defaultsRegistry(darkMode)
                .get(userBubble ? ColorKey.bubble_user : ColorKey.bubble_assistant);
        return color != null ? color : Color.LIGHT_GRAY;
    }

    /** The sunken background {@code colors.json} configures for the given theme. */
    public static Color configuredChatBackground(boolean darkMode) {
        Color color = defaultsRegistry(darkMode).get(ColorKey.sunken_background);
        return color != null ? color : Color.LIGHT_GRAY;
    }

    private static ColorRegistry defaultsRegistry(boolean darkMode) {
        ColorRegistry registry = darkMode ? darkDefaults : lightDefaults;
        if (registry != null) {
            return registry;
        }
        synchronized (DEFAULTS_LOCK) {
            registry = darkMode ? darkDefaults : lightDefaults;
            if (registry == null) {
                registry = ColorRegistry.fromJson(loadColorConfig(), darkMode);
                if (darkMode) {
                    darkDefaults = registry;
                } else {
                    lightDefaults = registry;
                }
            }
            return registry;
        }
    }

    // ---- color accessors ----------------------------------------------------

    public boolean isDark()                                     { return isDark; }
    public Color  background()                                   { return registry.get(ColorKey.background); }
    public Color  foreground()                                   { return registry.get(ColorKey.foreground); }
    public Color  selection()                                    { return registry.get(ColorKey.selection); }
    public Color  accent()                                       { return registry.get(ColorKey.accent); }
    public Color  sunkenBackground()                             { return registry.get(ColorKey.sunken_background); }
    public Color  bubbleUser()                                   { return registry.get(ColorKey.bubble_user); }
    public Color  bubbleAssistant()                              { return registry.get(ColorKey.bubble_assistant); }
    public Color  bubbleBorder()                                 { return registry.get(ColorKey.bubble_border); }
    public Color  assistantForeground()                          { return registry.get(ColorKey.assistant_foreground); }
    public Color  panelHeader()                                  { return registry.get(ColorKey.panel_header); }
    public Color  base1()                                        { return registry.get(ColorKey.base1); }
    public Color  base2()                                        { return registry.get(ColorKey.base2); }
    public Color  yellow()                                       { return registry.get(ColorKey.yellow); }
    public Color  codeBackground()                               { return registry.get(ColorKey.code_background); }
    public Color  codeForeground()                               { return registry.get(ColorKey.code_foreground); }
    public Color  codeSelection()                                { return registry.get(ColorKey.code_selection); }
    public Color  errorBackground()                              { return registry.get(ColorKey.error_background); }
    public Color  codeHeaderBackground()                         { return registry.get(ColorKey.code_header_background); }
    public Color  codeHeaderForeground()                         { return registry.get(ColorKey.code_header_foreground); }
    public Color  codeHeaderBorder()                             { return registry.get(ColorKey.code_header_border); }
    public Color  thinkingHeaderBackground()                     { return registry.get(ColorKey.thinking_header_background); }
    public Color  thinkingHeaderForeground()                     { return registry.get(ColorKey.thinking_header_foreground); }
    public Color  permissionBg()                                 { return registry.get(ColorKey.permission_bg); }
    public Color  permissionBorder()                             { return registry.get(ColorKey.permission_border); }
    public Color  permissionAccent()                             { return registry.get(ColorKey.permission_accent); }
    public Color  permissionFilename()                           { return registry.get(ColorKey.permission_filename); }
    public Color  permissionPath()                               { return registry.get(ColorKey.permission_path); }
    public Color  permissionTitle()                              { return registry.get(ColorKey.permission_title); }
    public Color  tableBackground()                              { return registry.get(ColorKey.table_background); }
    public Color  tableHeaderBackground()                        { return registry.get(ColorKey.table_header_background); }
    public Color  tableBorder()                                  { return registry.get(ColorKey.table_border); }
    public Color  tableRowAlternate()                            { return registry.get(ColorKey.table_row_alternate); }
    public Color  mutedForeground()                              { return registry.get(ColorKey.muted_foreground); }
    public Color  placeholderForeground()                        { return registry.get(ColorKey.placeholder_foreground); }
    public Color  collapsedHeaderForeground()                    { return registry.get(ColorKey.collapsed_header_foreground); }
    public Color  secondary2()                                    { return registry.get(ColorKey.secondary2); }
    public Color  inlineCodeForeground()                         { return registry.get(ColorKey.inline_code_foreground); }
    public Color  permissionGrantFg()                            { return registry.get(ColorKey.permission_grant_fg); }
    public Color  permissionGrantBg()                            { return registry.get(ColorKey.permission_grant_bg); }
    public Color  permissionGrantBorder()                        { return registry.get(ColorKey.permission_grant_border); }
    public Color  permissionDenyFg()                             { return registry.get(ColorKey.permission_deny_fg); }
    public Color  permissionDenyBg()                             { return registry.get(ColorKey.permission_deny_bg); }
    public Color  permissionDenyBorder()                         { return registry.get(ColorKey.permission_deny_border); }
    public Color  activityAccent()                               { return registry.get(ColorKey.activity_accent); }
    public Color  scrollButtonColor()                            { return registry.get(ColorKey.scroll_button_color); }

    // ---- CSS generation (delegated) -----------------------------------------

    /**
     * Generates the CSS for a chat bubble. Delegates to {@link CssGenerator}.
     * <p>Fast path reuses a cached result if the same (bg, fontSize, role)
     * combination was already generated.</p>
     */
    public String toCss(Color bubbleBg, boolean isAssistant, int fontSize) {
        return CssGenerator.generateCss(registry, bubbleBg, isAssistant, fontSize);
    }

    /**
     * Converts a single {@link Color} to its lowercase hex string.
     * Delegates to the shared cache in {@link CssGenerator}.
     */
    public String toHtmlHex(Color color) {
        return CssGenerator.toHtmlHex(color);
    }

    // ---- JSON loading -------------------------------------------------------

    private static JsonNode loadColorConfig() {
        try (InputStream in = ColorTheme.class.getResourceAsStream("colors.json")) {
            if (in == null) {
                throw new IllegalStateException(NbBundle.getMessage(UIBundle.class, "ERR_ColorsNotFound"));
            }
            return MapperSupplier.get().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(NbBundle.getMessage(UIBundle.class, "ERR_LoadColorsFailed"), e);
        }
    }
}
