package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AsciiArtPatternTest {
    @Test
    void detectsBoxDrawing() {
        assertTrue(AsciiArtPattern.findIn("┌───┐\n│ hi │"));
    }

    @Test
    void detectsDashRun() {
        assertTrue(AsciiArtPattern.findIn("-----"));
    }

    @Test
    void plainTextHasNoArt() {
        assertFalse(AsciiArtPattern.findIn("hello world"));
    }

    @Test
    void nullIsNotArt() {
        assertFalse(AsciiArtPattern.findIn(null));
    }
}
