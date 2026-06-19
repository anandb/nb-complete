package github.anandb.netbeans.ui;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.UIManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.support.MapperSupplier;
import org.openide.util.NbBundle;

/**
 * Centralized color definitions for the ACP NetBeans plugin.
 * Cached singleton refreshed on L&amp;F changes.
 */
public record ColorTheme(
        boolean isDark,
        Color background, Color foreground, Color selection, Color accent,
        Color ghostBackground, Color sunkenBackground, Color bubbleUser,
        Color bubbleAssistant, Color bubbleBorder, Color assistantForeground,
        Color panelHeader, Color panelHeaderHover, Color base1, Color base2, Color base3,
        Color yellow, Color codeBackground, Color codeForeground, Color codeSelection,
        Color headerForeground, Color errorBackground, Color codeHeaderBackground,
        Color codeHeaderForeground, Color codeHeaderBorder, Color thinkingHeaderBackground,
        Color thinkingHeaderForeground,
        Color toolForeground, Color permissionBg, Color permissionBorder, Color permissionTitle,
        Color tableBackground, Color tableHeaderBackground, Color tableBorder,
        Color mutedForeground, Color placeholderForeground, Color collapsedHeaderForeground,
        Color inlineCodeForeground,
        Color permissionGrantFg, Color permissionGrantBg, Color permissionGrantBorder,
        Color permissionDenyFg, Color permissionDenyBg, Color permissionDenyBorder,
        Color activityAccent, Color scrollButtonColor
) {

    private static volatile ColorTheme cachedTheme;

    /** Cache for {@link #toHtmlHex(Color)} results. Identity-based so distinct
     *  Color instances (even with same RGB) don't collide. Cleared on theme
     *  switch via the UIManager property change listener below. */
    private static final Map<Color, String> HEX_CACHE = new IdentityHashMap<>();

    static {
        UIManager.addPropertyChangeListener(e -> {
            cachedTheme = null;
            cachedCssAssistant = null;
            cachedCssUser = null;
            synchronized (ColorTheme.class) {
                HEX_CACHE.clear();
            }
            StyleResolver.clearCache();
            HtmlContentPreparer.clearCache();
        });
    }

    public static synchronized ColorTheme getNativeTheme(boolean darkMode) {
        ColorTheme theme = cachedTheme;
        if (theme != null) {
            return theme;
        }
        theme = createNativeTheme(darkMode);
        cachedTheme = theme;
        return theme;
    }

    private static ColorTheme createNativeTheme(boolean darkMode) {
        Map<String, Color> colors = resolveColors(darkMode);
        return new ColorTheme(
                darkMode,
                colors.get("background"), colors.get("foreground"), colors.get("selection"), colors.get("accent"),
                colors.get("ghostBackground"), colors.get("sunkenBackground"), colors.get("bubbleUser"),
                colors.get("bubbleAssistant"), colors.get("bubbleBorder"), colors.get("assistantForeground"),
                colors.get("panelHeader"), colors.get("panelHeaderHover"), colors.get("base1"), colors.get("base2"), colors.get("base3"),
                colors.get("yellow"), colors.get("codeBackground"), colors.get("codeForeground"), colors.get("codeSelection"),
                colors.get("headerForeground"), colors.get("errorBackground"), colors.get("codeHeaderBackground"),
                colors.get("codeHeaderForeground"), colors.get("codeHeaderBorder"), colors.get("thinkingHeaderBackground"),
                colors.get("thinkingHeaderForeground"),
                colors.get("toolForeground"), colors.get("permissionBg"), colors.get("permissionBorder"), colors.get("permissionTitle"),
                colors.get("tableBackground"), colors.get("tableHeaderBackground"), colors.get("tableBorder"),
                colors.get("mutedForeground"), colors.get("placeholderForeground"), colors.get("collapsedHeaderForeground"),
                colors.get("inlineCodeForeground"),
                colors.get("permissionGrantFg"), colors.get("permissionGrantBg"), colors.get("permissionGrantBorder"),
                colors.get("permissionDenyFg"), colors.get("permissionDenyBg"), colors.get("permissionDenyBorder"),
                colors.get("activityAccent"), colors.get("scrollButtonColor")
        );
    }

    private static Map<String, Color> resolveColors(boolean darkMode) {
        JsonNode config = loadColorConfig();
        Map<String, Color> colors = new LinkedHashMap<>();
        for (JsonNode entry : config) {
            String name = entry.has("name") ? entry.get("name").asText() : null;
            if (name == null) continue;
            String propName = entry.has("property") ? entry.get("property").asText() : null;
            Color fromProp = resolveFromProperty(propName);
            if (fromProp != null) {
                colors.put(name, fromProp);
                continue;
            }
            Color fromKey = resolveFromKey(entry, darkMode);
            if (fromKey != null) {
                colors.put(name, fromKey);
                continue;
            }
            Color fallback = resolveFallback(entry, darkMode);
            colors.put(name, fallback);
        }
        return colors;
    }

    private static Color resolveFromProperty(String propName) {
        if (propName == null) return null;
        String value = System.getProperty(propName);
        if (value != null && !value.isBlank()) {
            try {
                return Color.decode(value);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return null;
    }

    private static Color resolveFromKey(JsonNode entry, boolean darkMode) {
        String key = entry.has("key") ? entry.get("key").asText() : null;
        String keyDark = entry.has("keyDark") ? entry.get("keyDark").asText() : null;
        String keyLight = entry.has("keyLight") ? entry.get("keyLight").asText() : null;
        String effectiveKey = null;
        if (darkMode && keyDark != null) {
            effectiveKey = keyDark;
        } else if (!darkMode && keyLight != null) {
            effectiveKey = keyLight;
        } else if (key != null) {
            effectiveKey = key;
        }
        if (effectiveKey == null) return null;
        return UIManager.getColor(effectiveKey);
    }

    private static Color resolveFallback(JsonNode entry, boolean darkMode) {
        if (darkMode && entry.has("dark")) {
            return Color.decode(entry.get("dark").asText());
        }
        if (!darkMode && entry.has("light")) {
            return Color.decode(entry.get("light").asText());
        }
        if (entry.has("fallback")) {
            return Color.decode(entry.get("fallback").asText());
        }
        return null;
    }

    private static JsonNode loadColorConfig() {
        ObjectMapper mapper = MapperSupplier.get();
        try (InputStream in = ColorTheme.class.getResourceAsStream("colors.json")) {
            if (in == null) {
                throw new IllegalStateException(NbBundle.getMessage(ColorTheme.class, "ERR_ColorsNotFound"));
            }
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(NbBundle.getMessage(ColorTheme.class, "ERR_LoadColorsFailed"), e);
        }
    }

    private static volatile String cachedCssAssistant;
    private static volatile String cachedCssAssistantBg;
    private static volatile String cachedCssAssistantFontSize;
    private static volatile String cachedCssUser;
    private static volatile String cachedCssUserBg;
    private static volatile String cachedCssUserFontSize;

    public String toCss(Color bubbleBg, boolean isAssistant, int fontSize) {
        String bg = bubbleBg != null ? toHtmlHex(bubbleBg) : "transparent";
        String fontSizeStr = fontSize + "px";

        // Fast path: check cached CSS outside the synchronized block.
        if (isAssistant) {
            if (bg.equals(cachedCssAssistantBg) && fontSizeStr.equals(cachedCssAssistantFontSize) && cachedCssAssistant != null) {
                return cachedCssAssistant;
            }
        } else {
            if (bg.equals(cachedCssUserBg) && fontSizeStr.equals(cachedCssUserFontSize) && cachedCssUser != null) {
                return cachedCssUser;
            }
        }

        synchronized (ColorTheme.class) {
            // Recheck under lock in case another thread computed it.
            if (isAssistant) {
                if (bg.equals(cachedCssAssistantBg) && fontSizeStr.equals(cachedCssAssistantFontSize) && cachedCssAssistant != null) {
                    return cachedCssAssistant;
                }
            } else {
                if (bg.equals(cachedCssUserBg) && fontSizeStr.equals(cachedCssUserFontSize) && cachedCssUser != null) {
                    return cachedCssUser;
                }
            }

            String fg = toHtmlHex(assistantForeground());
            String linkColor = isDark() ? "#589DF6" : "#268BD2";
            String preBg = codeBackground() != null ? toHtmlHex(codeBackground()) : "#1e1f22";
            String preFg = codeForeground() != null ? toHtmlHex(codeForeground()) : "#bcbec4";

            String css = loadCssTemplate()
                    .replace("$fontFamily", UIUtils.fontStackWithActual())
                    .replace("$monoFamily", UIUtils.MONO_STACK)
                    .replace("$fontSize", fontSizeStr)
                    .replace("$fg", fg)
                    .replace("$bg", bg)
                    .replace("$linkColor", linkColor)
                    .replace("$codeBg", "rgba(255, 255, 255, 0.1)")
                    .replace("$preBg", preBg)
                    .replace("$preFg", preFg);

            if (isAssistant) {
                cachedCssAssistant = css;
                cachedCssAssistantBg = bg;
                cachedCssAssistantFontSize = fontSizeStr;
            } else {
                cachedCssUser = css;
                cachedCssUserBg = bg;
                cachedCssUserFontSize = fontSizeStr;
            }
            return css;
        }
    }

    private static volatile String cachedCssTemplate;

    private static synchronized String loadCssTemplate() {
        String template = cachedCssTemplate;
        if (template != null) {
            return template;
        }
        try (InputStream in = ColorTheme.class.getResourceAsStream("chat-style.css.template")) {
            if (in == null) {
                throw new IllegalStateException(NbBundle.getMessage(ColorTheme.class, "ERR_CssTemplateNotFound"));
            }
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            cachedCssTemplate = template;
            return template;
        } catch (IOException e) {
            throw new IllegalStateException(NbBundle.getMessage(ColorTheme.class, "ERR_LoadCssTemplateFailed"), e);
        }
    }

    public String toHtmlHex(Color color) {
        if (color == null) {
            return "#000000";
        }
        String cached = HEX_CACHE.get(color);
        if (cached != null) {
            return cached;
        }
        String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        // Only cache if cache is reasonably small to avoid unbounded growth
        if (HEX_CACHE.size() < 128) {
            HEX_CACHE.put(color, hex);
        }
        return hex;
    }
}
