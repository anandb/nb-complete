package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Box-drawing / block-element runs and long {@code -}/{@code =} runs. */
public final class AsciiArtPattern {
    private static final Pattern PATTERN = Pattern.compile("[\\u2500-\\u259F]|[-=]{5,}");

    private AsciiArtPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean findIn(CharSequence input) {
        return input != null && PATTERN.matcher(input).find();
    }
}
