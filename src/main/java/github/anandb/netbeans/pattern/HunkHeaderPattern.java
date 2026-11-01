package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unified diff hunk header ({@code @@ -a,b +c,d @@}). */
public final class HunkHeaderPattern {
    private static final Pattern PATTERN =
            Pattern.compile("^@@\\s+-(\\d+)(?:,\\d+)?\\s+\\+(\\d+)(?:,\\d+)?\\s+@@");

    private HunkHeaderPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
