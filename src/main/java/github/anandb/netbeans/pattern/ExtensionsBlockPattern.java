package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code ## Extensions} bullet list block in a startup banner. */
public final class ExtensionsBlockPattern {
    private static final Pattern PATTERN =
            Pattern.compile("## Extensions\\s*\n((?:- .+\n?(?:  - .+\n?)*)*)", Pattern.MULTILINE);

    private ExtensionsBlockPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
