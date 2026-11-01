package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Context-compression notice ({@code Compressed N messages into ...}). */
public final class CompressedContextPattern {
    private static final Pattern PATTERN = Pattern.compile("(Compressed [\\d]+ messages) into");

    private CompressedContextPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
