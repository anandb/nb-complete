package github.anandb.netbeans.ui;

import java.util.ArrayList;
import java.util.List;
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
     * The option id is checked before the label because a label is prose: Devin
     * reports kind {@code allow_always} for all five of its allow options and names
     * them "Allow", "Yes, allow `git reset` commands (this session)", "…in `bridge`",
     * "…in all projects" and "Yes, switch to bypass mode". Only the ids
     * ({@code allow_once}, {@code allow_session}, {@code allow_always},
     * {@code allow_always_global}, {@code switch_bypass}) order those scopes.
     * The global variant takes {@code 'P'} from "all projects" rather than
     * {@code 'G'} for global, so its label can underline the letter.
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
        String lid = id == null ? "" : id.toLowerCase(Locale.ROOT);
        String lname = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (kind == null) {
            return 0;
        }
        if (kind.contains("allow")) {
            if (lid.contains("session")) return 'S';
            if (lid.contains("global")) return 'P';
            if (lid.contains("bypass")) return 'B';
            if (lid.contains("always") || lname.contains("always")) return 'A';
            if (lid.contains("once") || lname.contains("once")) return 'O';
            return 'A';
        }
        if (kind.contains("reject") || kind.contains("deny")) {
            return lid.contains("always") || lname.contains("always") ? 'W' : 'R';
        }
        return 0;
    }

    /** A parsed option together with the mnemonic its row assigned to it. */
    record Choice(PermissionOption option, char mnemonic) {
    }

    /**
     * Parses an options array and assigns each entry the mnemonic its row gave it.
     * Both button builders iterate this, so neither repeats the parse-and-assign loop.
     */
    static List<Choice> choices(JsonNode options) {
        List<PermissionOption> parsed = new ArrayList<>(options.size());
        for (JsonNode opt : options) {
            parsed.add(fromJson(opt));
        }
        char[] letters = mnemonics(parsed);
        List<Choice> choices = new ArrayList<>(parsed.size());
        for (int i = 0; i < parsed.size(); i++) {
            choices.add(new Choice(parsed.get(i), letters[i]));
        }
        return choices;
    }

    /** Letters an allow option may fall back to, in preference order. */
    private static final String ALLOW_FALLBACKS = "OSPBFELT";
    /** Letters a reject option may fall back to. {@code 'D'} is excluded: Alt+D is Show Diff. */
    private static final String REJECT_FALLBACKS = "RNEYL";

    /**
     * Mnemonics for a whole button row, one letter per option ({@code 0} for none).
     * <p>
     * {@link #mnemonic()} is per option, so it cannot see the rest of the row. Devin's
     * request carries six options — "Allow", "Yes, allow `git reset` commands (this
     * session)", "…in `bridge`", "…in all projects", "Yes, switch to bypass mode",
     * "Reject" — and any two that resolve to the same letter make the later button
     * unreachable: Swing activates only the first mnemonic match.
     * <p>
     * The always-variants therefore claim their letter first, and every remaining
     * option then takes the first free letter that appears in its own label. Devin's
     * row reads Alt+O (allow), Alt+S (this session), Alt+A (this project), Alt+P (all
     * projects), Alt+B (bypass mode), Alt+R (reject); a lone "Allow" still keeps
     * Alt+A because nothing else competes for it.
     */
    static char[] mnemonics(List<PermissionOption> options) {
        char[] letters = new char[options.size()];
        boolean[] taken = new boolean[26];
        List<Integer> order = new ArrayList<>(options.size());
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < options.size(); i++) {
                if (isAlways(options.get(i)) == (pass == 0)) {
                    order.add(i);
                }
            }
        }
        for (int i : order) {
            char letter = pick(options.get(i), taken);
            letters[i] = letter;
            if (letter != 0) {
                taken[letter - 'A'] = true;
            }
        }
        return letters;
    }

    private static boolean isAlways(PermissionOption option) {
        // The id is the only scope signal: Devin reports kind allow_always for
        // every allow option, so a kind/name test puts the whole row in one pass
        // and the priority below becomes array order.
        String lid = option.id() == null ? "" : option.id().toLowerCase(Locale.ROOT);
        if (lid.contains("always") || lid.contains("global") || lid.contains("bypass")) {
            return true;
        }
        String lname = option.name() == null ? "" : option.name().toLowerCase(Locale.ROOT);
        return lid.isEmpty() && lname.contains("always");
    }

    private static char pick(PermissionOption option, boolean[] taken) {
        char preferred = option.mnemonic();
        if (preferred == 0) {
            return 0;
        }
        if (!taken[preferred - 'A']) {
            return preferred;
        }
        // Only the fallbacks have to be visible in the label: a preferred letter is
        // the documented keystroke even when the label cannot underline it ("Deny").
        String fallbacks = fallbacks(option);
        String lname = option.name() == null ? "" : option.name().toLowerCase(Locale.ROOT);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < fallbacks.length(); i++) {
                char candidate = fallbacks.charAt(i);
                if (taken[candidate - 'A']) {
                    continue;
                }
                if (pass == 0 && lname.indexOf(Character.toLowerCase(candidate)) < 0) {
                    continue;
                }
                return candidate;
            }
        }
        return 0;
    }

    private static String fallbacks(PermissionOption option) {
        String kind = option.kind() == null ? "" : option.kind();
        if (kind.contains("allow")) {
            return ALLOW_FALLBACKS;
        }
        if (kind.contains("reject") || kind.contains("deny")) {
            return REJECT_FALLBACKS;
        }
        return "";
    }
}
