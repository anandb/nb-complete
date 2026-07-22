package github.anandb.netbeans.ui;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import github.anandb.netbeans.model.ColorKey;
import github.anandb.netbeans.model.ColorRegistry;
import org.openide.util.NbBundle;

/**
 * Generates &amp; caches the CSS injected into chat HTML bubbles.
 * Separated from {@link ColorTheme} to keep data and rendering apart.
 */
public final class CssGenerator {

    private CssGenerator() {}

    // ---- template loading ---------------------------------------------------

    private static volatile String cachedCssTemplate;

    public static synchronized String loadCssTemplate() {
        String template = cachedCssTemplate;
        if (template != null) {
            return template;
        }
        try (InputStream in = CssGenerator.class.getResourceAsStream("chat-style.css.template")) {
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

    /** Invalidates the cached template (called on theme switch). */
    static void clearCssTemplateCache() {
        cachedCssTemplate = null;
    }

    // ---- CSS generation -----------------------------------------------------

    private static volatile String cachedCssAssistant;
    private static volatile String cachedCssAssistantBg;
    private static volatile String cachedCssAssistantFontSize;
    private static volatile String cachedCssUser;
    private static volatile String cachedCssUserBg;
    private static volatile String cachedCssUserFontSize;

    /** Invalidates the generated CSS caches (called on theme switch). */
    static void clearGeneratedCss() {
        cachedCssAssistant = null;
        cachedCssUser = null;
    }

    /**
     * Generates the CSS string for a chat bubble. Uses a simple 2-entry cache
     * keyed by (role, bg hex, fontSize).
     */
    public static String generateCss(ColorRegistry registry, Color bubbleBg,
                                     boolean isAssistant, int fontSize) {
        String bg = bubbleBg != null ? toHtmlHex(bubbleBg) : "transparent";
        String fontSizeStr = fontSize + "px";

        // Fast path: check cached CSS outside lock
        if (isAssistant) {
            if (bg.equals(cachedCssAssistantBg)
                    && fontSizeStr.equals(cachedCssAssistantFontSize)
                    && cachedCssAssistant != null) {
                return cachedCssAssistant;
            }
        } else {
            if (bg.equals(cachedCssUserBg)
                    && fontSizeStr.equals(cachedCssUserFontSize)
                    && cachedCssUser != null) {
                return cachedCssUser;
            }
        }

        synchronized (CssGenerator.class) {
            // Recheck under lock
            if (isAssistant) {
                if (bg.equals(cachedCssAssistantBg)
                        && fontSizeStr.equals(cachedCssAssistantFontSize)
                        && cachedCssAssistant != null) {
                    return cachedCssAssistant;
                }
            } else {
                if (bg.equals(cachedCssUserBg)
                        && fontSizeStr.equals(cachedCssUserFontSize)
                        && cachedCssUser != null) {
                    return cachedCssUser;
                }
            }

            String fg = registry.toHex(ColorKey.assistant_foreground);
            String linkColor = registry.isDark() ? "#589DF6" : "#268BD2";
            String preBg = registry.get(ColorKey.code_background) != null
                    ? registry.toHex(ColorKey.code_background) : "#1e1f22";
            String preFg = registry.get(ColorKey.code_foreground) != null
                    ? registry.toHex(ColorKey.code_foreground) : "#bcbec4";

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

    /**
     * Converts a single {@link Color} to its lowercase hex string.
     * Delegates to a shared static cache for efficiency.
     */
    public static String toHtmlHex(Color color) {
        if (color == null) {
            return "#000000";
        }
        return HEX_CACHE.get(color, c -> String.format("#%02x%02x%02x",
                c.getRed(), c.getGreen(), c.getBlue()));
    }

    /** Shared hex cache (static because it maps java.awt.Color, not theme state). */
    private static final Cache<Color, String> HEX_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(128)
                    .expireAfterAccess(60, TimeUnit.MINUTES)
                    .build();

    /** Invalidates the shared hex cache (called on theme switch). */
    static void clearHexCache() {
        HEX_CACHE.invalidateAll();
    }
}
