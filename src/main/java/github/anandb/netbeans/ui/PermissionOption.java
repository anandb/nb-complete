package github.anandb.netbeans.ui;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parsed option from a permission request payload.
 */
record PermissionOption(String id, String name, String kind) {
    static PermissionOption fromJson(JsonNode opt) {
        String optionId = opt.has("optionId") ? opt.get("optionId").asText() : "";
        String name = opt.has("name") ? opt.get("name").asText() : optionId;
        String kind = opt.has("kind") ? opt.get("kind").asText() : "";
        return new PermissionOption(optionId, name, kind);
    }

    /**
     * Keyboard mnemonic for the button that answers this option, or {@code 0} when the
     * option carries an unknown kind and should have none.
     * <p>
     * Harnesses send their own labels, so allow options arrive as "Allow Once" and
     * "Always Allow" (or "allow_once"/"allow_always"). Giving both {@code 'A'} made
     * Alt+A ambiguous, and Swing only activates the first match. The <em>always</em>
     * variant therefore takes a letter from "Always" ({@code 'A'} for allow,
     * {@code 'W'} for reject) while the once/lone variant keeps the verb's letter
     * ({@code 'O'} for allow, {@code 'R'} for reject). Every letter appears in the
     * usual labels, so Swing underlines it.
     * <p>
     * {@code 'L'} is deliberately avoided for the always-reject button: the plugin
     * binds Alt+L to toggle the mini assistant, and a destructive "stop asking me"
     * button must not sit on the same keystroke.
     * <p>
     * A reject button labelled "Deny" deliberately keeps {@code 'R'} rather than
     * {@code 'D'}: "Show Diff" sits in the same button row and has always used
     * Alt+D, and the two would collide on every edit request.
     */
    char mnemonic() {
        String lname = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (kind == null) {
            return 0;
        }
        if (kind.contains("allow")) {
            if (lname.contains("always")) return 'A';
            if (lname.contains("once")) return 'O';
            return 'A';
        }
        if (kind.contains("reject") || kind.contains("deny")) {
            return lname.contains("always") ? 'W' : 'R';
        }
        return 0;
    }
}
