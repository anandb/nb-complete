package github.anandb.netbeans.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlContentPreparerAsciiArtTest {

    @BeforeAll
    static void setUp() {
        TestUiUtils.setupTestUIManager();
    }

    @Test
    void asciiArtCssIsInsideStyleBlock() {
        ColorTheme theme = ThemeManager.getCurrentTheme();

        // Markdown with a separator row containing 6 dashes triggers ASCII art detection.
        String markdown = "| a | b |\n| --- | ------ |\n| 1 | 2 |";
        String html = HtmlContentPreparer.prepareHtml(markdown, theme, "assistant", false);

        // The .ascii-art rule must be between <style> and </style>, not after </style>.
        int styleOpen = html.indexOf("<style>");
        int styleClose = html.indexOf("</style>");
        int asciiRule = html.indexOf(".ascii-art");

        assertTrue(styleOpen >= 0, "<style> tag should be present");
        assertTrue(styleClose > styleOpen, "</style> tag should be present after <style>");
        assertTrue(asciiRule >= 0, ".ascii-art rule should be present");
        assertTrue(asciiRule > styleOpen && asciiRule < styleClose,
                ".ascii-art rule should be inside <style> block, but was at index " + asciiRule
                        + " with <style> at " + styleOpen + " and </style> at " + styleClose
                        + "\nHTML:\n" + html);

        // The rule text must NOT appear as visible body content.
        String afterStyle = html.substring(styleClose);
        assertFalse(afterStyle.contains(".ascii-art"),
                ".ascii-art rule should not appear after </style>\nAfter </style>:" + afterStyle);
    }
}
