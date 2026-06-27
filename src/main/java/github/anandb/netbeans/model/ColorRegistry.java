package github.anandb.netbeans.model;

import java.awt.Color;
import java.util.EnumMap;
import java.util.concurrent.TimeUnit;
import javax.swing.UIManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Container for all theme colors loaded from colors.json.
 * Map-based (not positional record), type-safe via {@link ColorKey}.
 * Each instance owns its own hex cache.
 */
public final class ColorRegistry {

    private final boolean isDark;
    private final EnumMap<ColorKey, Color> colors;

    /** Per-instance hex cache — cleared when this registry is replaced. */
    private final Cache<ColorKey, String> hexCache =
            Caffeine.newBuilder()
                    .maximumSize(128)
                    .expireAfterAccess(60, TimeUnit.MINUTES)
                    .build();

    private ColorRegistry(boolean isDark, EnumMap<ColorKey, Color> colors) {
        this.isDark = isDark;
        this.colors = colors;
    }

    /** Returns the dark-mode flag associated with this registry. */
    public boolean isDark() {
        return isDark;
    }

    /** Returns the Color for the given key, or {@code null} if undefined. */
    public Color get(ColorKey key) {
        return colors.get(key);
    }

    /**
     * Returns the lowercase hex string (e.g. {@code "#ff8800"}) for the given
     * key, cached per registry instance.
     */
    public String toHex(ColorKey key) {
        Color color = get(key);
        if (color == null) {
            return "#000000";
        }
        return hexCache.get(key, k -> String.format("#%02x%02x%02x",
                color.getRed(), color.getGreen(), color.getBlue()));
    }

    // ---- JSON loading & color resolution --------------------------------

    /**
     * Builds a ColorRegistry from a colors.json config node.
     * Resolution order: System property → UIManager key → hardcoded light/dark fallback.
     */
    public static ColorRegistry fromJson(JsonNode config, boolean darkMode) {
        EnumMap<ColorKey, Color> map = new EnumMap<>(ColorKey.class);
        for (JsonNode entry : config) {
            String name = entry.has("name") ? entry.get("name").asText() : null;
            if (name == null) continue;
            ColorKey key = ColorKey.fromJsonName(name);
            if (key == null) continue; // unknown name — skip silently
            Color color = resolveColor(entry, darkMode);
            if (color != null) {
                map.put(key, color);
            }
        }
        return new ColorRegistry(darkMode, map);
    }

    private static Color resolveColor(JsonNode entry, boolean darkMode) {
        Color fromProp = resolveFromProperty(entry, darkMode);
        if (fromProp != null) return fromProp;
        Color fromKey = resolveFromKey(entry, darkMode);
        if (fromKey != null) return fromKey;
        return resolveFallback(entry, darkMode);
    }

    private static Color resolveFromProperty(JsonNode entry, boolean darkMode) {
        String propName = entry.has("property") ? entry.get("property").asText() : null;
        String propDark = entry.has("propertyDark") ? entry.get("propertyDark").asText() : null;
        String propLight = entry.has("propertyLight") ? entry.get("propertyLight").asText() : null;
        String effective = null;
        if (darkMode && propDark != null) {
            effective = propDark;
        } else if (!darkMode && propLight != null) {
            effective = propLight;
        } else if (propName != null) {
            effective = propName;
        }
        if (effective == null) return null;
        String value = System.getProperty(effective);
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
        String effective = null;
        if (darkMode && keyDark != null) {
            effective = keyDark;
        } else if (!darkMode && keyLight != null) {
            effective = keyLight;
        } else if (key != null) {
            effective = key;
        }
        if (effective == null) return null;
        return UIManager.getColor(effective);
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
}
