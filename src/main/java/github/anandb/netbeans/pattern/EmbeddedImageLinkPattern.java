package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Markdown image or link carrying an embedded base64 image data URL. */
public final class EmbeddedImageLinkPattern {
    private static final Pattern PATTERN = Pattern.compile(
            "!?\\[([^\\]\\r\\n]*)\\]\\(data:(image/[A-Za-z0-9.+-]+);base64,[A-Za-z0-9+/=\\r\\n]+\\)",
            Pattern.CASE_INSENSITIVE);

    private EmbeddedImageLinkPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }
}
