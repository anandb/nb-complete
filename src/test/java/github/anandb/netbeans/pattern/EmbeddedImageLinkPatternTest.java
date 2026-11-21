package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

class EmbeddedImageLinkPatternTest {

    @Test
    void matchesImageLinkAndCapturesAltText() {
        Matcher matcher = EmbeddedImageLinkPattern.matcher(
                "![diagram](data:image/png;base64,AAECAw==)");
        assertTrue(matcher.find());
        assertEquals("diagram", matcher.group(1));
    }

    @Test
    void matchesBareLinkInsideText() {
        Matcher matcher = EmbeddedImageLinkPattern.matcher(
                "see [photo](data:image/jpeg;base64,BBBB) for details");
        assertTrue(matcher.find());
        assertEquals("photo", matcher.group(1));
    }

    @Test
    void mediaTypeIsCaseInsensitive() {
        assertTrue(EmbeddedImageLinkPattern.matcher(
                "![x](DATA:IMAGE/SVG+XML;BASE64,AAAA)").find());
    }

    @Test
    void doesNotMatchOrdinaryLink() {
        assertFalse(EmbeddedImageLinkPattern.matcher(
                "[doc](https://example.com/x.png)").find());
    }
}
