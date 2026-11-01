package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WhitespacePatternTest {
    @Test
    void splitsOnWhitespace() {
        assertArrayEquals(new String[] {"a", "b"}, WhitespacePattern.split("a  b"));
    }

    @Test
    void splitWithLimit() {
        assertArrayEquals(new String[] {"a", "b c"}, WhitespacePattern.split("a b c", 2));
    }

    @Test
    void collapsesAndTrims() {
        assertEquals("a b", WhitespacePattern.collapseToSingleSpaces("  a\n\tb  "));
    }

    @Test
    void nullCollapsesToEmpty() {
        assertEquals("", WhitespacePattern.collapseToSingleSpaces(null));
    }
}
