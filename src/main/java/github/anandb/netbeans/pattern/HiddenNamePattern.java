package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File names hidden from the mdproject tree (dotfiles, OS and editor metadata). */
public final class HiddenNamePattern {
    private static final Pattern PATTERN = Pattern.compile(
        "(?i)^\\.(?!gitignore|gitattributes|editorconfig|npmrc|hgignore)"
        + "|^Thumbs\\.db$|^Desktop\\.ini$|^\\.DS_Store$"
        + "|\\.(?:swp|swo|bak|~)$");

    private HiddenNamePattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean isHidden(String name) {
        return name != null && PATTERN.matcher(name).find();
    }
}
