package github.anandb.netbeans.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.support.MapperSupplier;

/**
 * Colour precedence: a system property wins over an Options setting, and the setting wins over
 * colors.json. The light/dark property names in the config decide which override applies.
 */
class ColorRegistryOverrideTest {

    private static final String LIGHT_PROPERTY = "beanbot.test.color.light";
    private static final String DARK_PROPERTY = "beanbot.test.color.dark";

    @AfterEach
    void clearProperty() {
        System.clearProperty(LIGHT_PROPERTY);
        System.clearProperty(DARK_PROPERTY);
    }

    @Test
    void settingOverridesTheConfiguredColour() throws Exception {
        ColorRegistry registry = ColorRegistry.fromJson(config(), false,
                Map.of(LIGHT_PROPERTY, "#123456"));

        assertEquals(new Color(0x12, 0x34, 0x56), registry.get(ColorKey.bubble_user));
    }

    @Test
    void systemPropertyBeatsTheSetting() throws Exception {
        System.setProperty(LIGHT_PROPERTY, "#ff0000");

        ColorRegistry registry = ColorRegistry.fromJson(config(), false,
                Map.of(LIGHT_PROPERTY, "#123456"));

        assertEquals(new Color(0xff, 0x00, 0x00), registry.get(ColorKey.bubble_user));
    }

    @Test
    void darkThemeUsesTheDarkPropertyName() throws Exception {
        ColorRegistry registry = ColorRegistry.fromJson(config(), true,
                Map.of(DARK_PROPERTY, "#0d0e0f"));

        assertEquals(new Color(0x0d, 0x0e, 0x0f), registry.get(ColorKey.bubble_user));
    }

    @Test
    void blankSettingFallsBackToTheConfiguredColour() throws Exception {
        ColorRegistry registry = ColorRegistry.fromJson(config(), false,
                Map.of(LIGHT_PROPERTY, "  "));

        assertEquals(new Color(0xe9, 0xe8, 0xe2), registry.get(ColorKey.bubble_user));
    }

    private static JsonNode config() throws Exception {
        String json = """
                [{
                  "name": "bubbleUser",
                  "propertyLight": "%s",
                  "propertyDark": "%s",
                  "dark": "#2D2A23",
                  "light": "#E9E8E2"
                }]
                """.formatted(LIGHT_PROPERTY, DARK_PROPERTY);
        return MapperSupplier.get().readTree(json);
    }
}
