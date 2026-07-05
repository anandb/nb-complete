package github.anandb.netbeans.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Type-safe tokens for every color in the theme system.
 * Maps one-to-one to entries in colors.json (excluding 3 dead fields:
 * ghostBackground, panelHeaderHover, base3).
 *
 * <p>Lower-case with underscores for visual clarity in source.
 */
public enum ColorKey {

    background("background"),
    foreground("foreground"),
    selection("selection"),
    accent("accent"),
    sunken_background("sunkenBackground"),
    bubble_user("bubbleUser"),
    bubble_assistant("bubbleAssistant"),
    bubble_border("bubbleBorder"),
    assistant_foreground("assistantForeground"),
    panel_header("panelHeader"),
    header_foreground("headerForeground"),
    base1("base1"),
    base2("base2"),
    yellow("yellow"),
    code_background("codeBackground"),
    code_foreground("codeForeground"),
    code_selection("codeSelection"),
    error_background("errorBackground"),
    code_header_background("codeHeaderBackground"),
    code_header_foreground("codeHeaderForeground"),
    code_header_border("codeHeaderBorder"),
    thinking_header_background("thinkingHeaderBackground"),
    thinking_header_foreground("thinkingHeaderForeground"),
    tool_foreground("toolForeground"),
    permission_bg("permissionBg"),
    permission_border("permissionBorder"),
    permission_title("permissionTitle"),
    table_background("tableBackground"),
    table_header_background("tableHeaderBackground"),
    table_border("tableBorder"),
    table_row_alternate("tableRowAlternate"),
    muted_foreground("mutedForeground"),
    placeholder_foreground("placeholderForeground"),
    collapsed_header_foreground("collapsedHeaderForeground"),
    inline_code_foreground("inlineCodeForeground"),
    permission_grant_fg("permissionGrantFg"),
    permission_grant_bg("permissionGrantBg"),
    permission_grant_border("permissionGrantBorder"),
    permission_deny_fg("permissionDenyFg"),
    permission_deny_bg("permissionDenyBg"),
    permission_deny_border("permissionDenyBorder"),
    activity_accent("activityAccent"),
    scroll_button_color("scrollButtonColor");

    /** The name as it appears in colors.json. */
    public final String jsonName;

    ColorKey(String jsonName) {
        this.jsonName = jsonName;
    }

    private static final Map<String, ColorKey> BY_JSON_NAME = new HashMap<>();

    static {
        for (ColorKey key : values()) {
            BY_JSON_NAME.put(key.jsonName, key);
        }
    }

    /**
     * Returns the ColorKey for a JSON entry name, or {@code null} if unknown.
     */
    public static ColorKey fromJsonName(String name) {
        return BY_JSON_NAME.get(name);
    }
}
