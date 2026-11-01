package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiled glob ({@code *} = any run, {@code ?} = any single char).
 * All other characters are escaped literally.
 */
public final class GlobPattern {
    private final Pattern compiled;

    private GlobPattern(Pattern compiled) {
        this.compiled = compiled;
    }

    public static GlobPattern compile(String glob) {
        return new GlobPattern(Pattern.compile(toRegex(glob)));
    }

    public Matcher matcher(CharSequence input) {
        return compiled.matcher(input);
    }

    public boolean matches(String input) {
        return input != null && compiled.matcher(input).matches();
    }

    /** Converts a glob to a regex string, escaping every other character. */
    static String toRegex(String glob) {
        StringBuilder regex = new StringBuilder(glob.length() * 2);
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\[](){}<>^$|.?*+-=".indexOf(c) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
        }
        return regex.toString();
    }
}
