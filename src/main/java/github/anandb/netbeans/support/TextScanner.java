package github.anandb.netbeans.support;

import java.util.regex.Pattern;

/**
 * Utility class for scanning text for specific patterns like ASCII art.
 */
public class TextScanner {
    private static final Logger LOG = new Logger(TextScanner.class);
    
    // Combined range for Box Drawing (2500-257F) and Block Elements (2580-259F)
    private static final Pattern ASCII_ART_PATTERN = Pattern.compile("[\\u2500-\\u259F]|[-=]{5,}");

    /**
     * Checks if the given text contains ASCII art or box-drawing characters.
     * 
     * @param text the text to scan
     * @return true if ASCII art is detected, false otherwise
     */
    public static boolean containsAsciiArt(String text) {
        if (text == null) {
            return false;
        }
        boolean match = ASCII_ART_PATTERN.matcher(text).find();
        if (match) {
            LOG.fine("ASCII Art detected in text (length: {0})", text.length());
        }
        return match;
    }
}
