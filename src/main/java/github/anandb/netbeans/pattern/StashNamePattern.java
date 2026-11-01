package github.anandb.netbeans.pattern;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stash entry name with captured index, e.g. {@code stash@{0}}. */
public final class StashNamePattern {
    private static final Pattern PATTERN = Pattern.compile("stash@\\{(\\d+)\\}");

    private StashNamePattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static OptionalInt extractIndex(String displayName) {
        if (displayName == null) {
            return OptionalInt.empty();
        }
        Matcher m = PATTERN.matcher(displayName);
        if (!m.find()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(m.group(1)));
    }
}
