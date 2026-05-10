package github.anandb.netbeans.ui;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.UIManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.support.MapperSupplier;

/**
 * Centralized color definitions for the ACP NetBeans plugin.
 * Cached singleton refreshed on L&F changes.
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
        Color tableBackground, Color tableHeaderBackground, Color tableBorder
) {

    private static volatile ColorTheme cachedTheme;
    static {
        UIManager.addPropertyChangeListener(e -> {
            cachedTheme = null;
            cachedCssAssistant = null;
            cachedCssUser = null;
        });
    }

    public static ColorTheme getNativeTheme(boolean darkMode) {
        ColorTheme theme = cachedTheme;
        if (theme != null) {
            return theme;
        }
        theme = createNativeTheme(darkMode);
        cachedTheme = theme;
        return theme;
    }

    private static ColorTheme createNativeTheme(boolean darkMode) {
        List<Color> colors = resolveColors(darkMode);
        return new ColorTheme(
                darkMode,
                colors.get(0), colors.get(1), colors.get(2), colors.get(3),
                colors.get(4), colors.get(5), colors.get(6),
                colors.get(7), colors.get(8), colors.get(9),
                colors.get(10), colors.get(11), colors.get(12), colors.get(13), colors.get(14),
                colors.get(15), colors.get(16), colors.get(17), colors.get(18),
                colors.get(19), colors.get(20), colors.get(21),
                colors.get(22), colors.get(23), colors.get(24),
                colors.get(25),
                colors.get(26), colors.get(27), colors.get(28), colors.get(29),
                colors.get(30), colors.get(31), colors.get(32)
        );
    }

    private static List<Color> resolveColors(boolean darkMode) {
        JsonNode config = loadColorConfig();
        List<Color> colors = new ArrayList<>();
        for (JsonNode entry : config) {
            String propName = entry.has("property") ? entry.get("property").asText() : null;
            Color fromProp = resolveFromProperty(propName);
            if (fromProp != null) {
                colors.add(fromProp);
                continue;
            }
            Color fromKey = resolveFromKey(entry, darkMode);
            if (fromKey != null) {
                colors.add(fromKey);
                continue;
            }
            Color fallback = resolveFallback(entry, darkMode);
            colors.add(fallback);
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
                throw new IllegalStateException("colors.json not found");
            }
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load colors.json", e);
        }
    }

    private static volatile String cachedCssAssistant;
    private static volatile String cachedCssAssistantBg;
    private static volatile String cachedCssUser;
    private static volatile String cachedCssUserBg;

    public String toCss(Color bubbleBg, boolean isAssistant) {
        String bg = bubbleBg != null ? toHtmlHex(bubbleBg) : "transparent";

        if (isAssistant) {
            if (bg.equals(cachedCssAssistantBg) && cachedCssAssistant != null) {
                return cachedCssAssistant;
            }
        } else {
            if (bg.equals(cachedCssUserBg) && cachedCssUser != null) {
                return cachedCssUser;
            }
        }

        String fg = toHtmlHex(isAssistant ? assistantForeground() : foreground());
        String linkColor = isDark() ? "#589DF6" : "#268BD2";
        String preBg = codeBackground() != null ? toHtmlHex(codeBackground()) : "#1e1f22";
        String preFg = codeForeground() != null ? toHtmlHex(codeForeground()) : "#bcbec4";

        String css = loadCssTemplate()
                .replace("$fg", fg)
                .replace("$bg", bg)
                .replace("$linkColor", linkColor)
                .replace("$codeBg", "rgba(255, 255, 255, 0.1)")
                .replace("$preBg", preBg)
                .replace("$preFg", preFg);

        if (isAssistant) {
            cachedCssAssistant = css;
            cachedCssAssistantBg = bg;
        } else {
            cachedCssUser = css;
            cachedCssUserBg = bg;
        }
        return css;
    }

    private static volatile String cachedCssTemplate;

    private static String loadCssTemplate() {
        String template = cachedCssTemplate;
        if (template != null) {
            return template;
        }
        try (InputStream in = ColorTheme.class.getResourceAsStream("chat-style.css.template")) {
            if (in == null) {
                throw new IllegalStateException("chat-style.css.template not found");
            }
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            cachedCssTemplate = template;
            return template;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load chat-style.css.template", e);
        }
    }

    public String toHtmlHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
