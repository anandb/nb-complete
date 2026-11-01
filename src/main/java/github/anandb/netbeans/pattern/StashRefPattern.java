package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stash node display name, with any trailing suffix. */
public final class StashRefPattern {
    private static final Pattern PATTERN = Pattern.compile("stash@\\{\\d+\\}.*");

    private StashRefPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean matches(String input) {
        return input != null && PATTERN.matcher(input).matches();
    }
}
