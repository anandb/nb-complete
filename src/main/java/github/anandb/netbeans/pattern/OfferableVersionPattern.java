package github.anandb.netbeans.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Version the update channel may offer: numeric core, optional {@code -RELEASE}. */
public final class OfferableVersionPattern {
    private static final Pattern PATTERN =
            Pattern.compile("\\d+(?:\\.\\d+)*(?:[-.]RELEASE)?", Pattern.CASE_INSENSITIVE);

    private OfferableVersionPattern() {}

    public static Matcher matcher(CharSequence input) {
        return PATTERN.matcher(input);
    }

    public static boolean matches(String version) {
        return version != null && PATTERN.matcher(version.trim()).matches();
    }
}
