package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTML comment stripped from message text. */
public final class HtmlCommentPattern {
    private static final Pattern PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private HtmlCommentPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static String stripFrom(String text) {
        return text == null ? null : PATTERN.matcher(text).replaceAll("");
    }
}
