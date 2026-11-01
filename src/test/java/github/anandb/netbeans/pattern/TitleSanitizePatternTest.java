package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TitleSanitizePatternTest {
    @Test
    void replacesUnsafeChars() {
        assertEquals("a_b_c", TitleSanitizePattern.sanitize("a/b:c"));
    }

    @Test
    void keepsSafeChars() {
        assertEquals("aZ0._-x", TitleSanitizePattern.sanitize("aZ0._-x"));
    }

    @Test
    void nullSanitizesToEmpty() {
        assertEquals("", TitleSanitizePattern.sanitize(null));
    }
}
