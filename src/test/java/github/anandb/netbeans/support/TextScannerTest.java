package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TextScannerTest {

    @Test
    public void testContainsAsciiArt() {
        // Box drawing characters
        assertTrue(TextScanner.containsAsciiArt("┌───┐\n│art│\n└───┘"));
        assertTrue(TextScanner.containsAsciiArt("│ DCP Sweep"));

        // Long dash/equal sequences
        assertTrue(TextScanner.containsAsciiArt("-----"));
        assertTrue(TextScanner.containsAsciiArt("====="));

        // Regular text
        assertFalse(TextScanner.containsAsciiArt("Hello World"));
        assertFalse(TextScanner.containsAsciiArt("---")); // Too short
        assertFalse(TextScanner.containsAsciiArt("====")); // Too short
        assertFalse(TextScanner.containsAsciiArt(null));
        assertFalse(TextScanner.containsAsciiArt(""));

        // Mixed text
        assertTrue(TextScanner.containsAsciiArt("Some text before ┌───┐ and after"));

        assertTrue(TextScanner.containsAsciiArt("╭───────────────────────────────────────────────────────────╮"));
        assertTrue(TextScanner.containsAsciiArt("╭───────────────────────────────────────────────────────────╮\n│                      DCP Sweep                            │\n╰───────────────────────────────────────────────────────────╯"));
    }
}
