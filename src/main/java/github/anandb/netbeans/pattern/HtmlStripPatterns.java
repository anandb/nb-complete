package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTML tags and entities for plain-text extraction from test output. */
public final class HtmlStripPatterns {
    private static final Pattern TAG = Pattern.compile("<(?=[a-zA-Z/!])[^>]*>");
    private static final Pattern NBSP = Pattern.compile("&nbsp;");
    private static final Pattern LT = Pattern.compile("&lt;");
    private static final Pattern GT = Pattern.compile("&gt;");
    private static final Pattern AMP = Pattern.compile("&amp;");
    private static final Pattern QUOT = Pattern.compile("&quot;");
    private static final Pattern APOS = Pattern.compile("&#39;");

    private HtmlStripPatterns() {}

    public static Matcher tagMatcher(CharSequence input) {
        return TAG.matcher(input);
    }

    /** Strips tags and unescapes entities, preserving {@code expected:<1>} text. */
    public static String stripHtml(String text) {
        if (text == null) {
            return null;
        }
        String s = TAG.matcher(text).replaceAll("");
        s = NBSP.matcher(s).replaceAll(" ");
        s = LT.matcher(s).replaceAll("<");
        s = GT.matcher(s).replaceAll(">");
        s = AMP.matcher(s).replaceAll("&");
        s = QUOT.matcher(s).replaceAll("\"");
        s = APOS.matcher(s).replaceAll("'");
        return s;
    }
}
