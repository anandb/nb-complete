package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code ## Context} bullet list block in a startup banner. */
public final class ContextBlockPattern {
    private static final Pattern PATTERN =
            Pattern.compile("## Context\\s*\n((?:- .+\n?)+)", Pattern.MULTILINE);

    private ContextBlockPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
