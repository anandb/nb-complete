package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code <metadata>} block stripped from message text. */
public final class MetadataBlockPattern {
    private static final Pattern PATTERN = Pattern.compile("<metadata>.*?</metadata>", Pattern.DOTALL);

    private MetadataBlockPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String stripFrom(String text) {
        return text == null ? null : PATTERN.matcher(text).replaceAll("");
    }
}
