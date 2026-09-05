package github.anandb.netbeans.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlContentPreparerUserMessageTest {

    @BeforeAll
    static void setUp() {
        TestUiUtils.setupTestUIManager();
    }

    @Test
    void userMessageDoesNotInterpretMarkdown() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String markdown = "*hello* and _world_";
        String html = HtmlContentPreparer.prepareHtml(markdown, theme, "user", false);

        assertFalse(html.contains("<em>"),
                "User messages should not interpret markdown italics\n" + html);
        assertTrue(html.contains("*hello*"),
                "User messages should render markdown metacharacters literally\n" + html);
    }

    @Test
    void userMessagePreservesIndentationAndSpaces() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String text = "  two spaces\n    four spaces";
        String html = HtmlContentPreparer.prepareHtml(text, theme, "user", false);

        assertTrue(html.contains("&nbsp;"),
                "Leading spaces should be preserved as non-breaking spaces\n" + html);
        assertTrue(html.contains("<p"),
                "Lines should be wrapped in <p> blocks for correct Swing sizing\n" + html);
    }

    @Test
    void userMessagePreservesMultipleInternalSpaces() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String text = "word1    word2";
        String html = HtmlContentPreparer.prepareHtml(text, theme, "user", false);

        // Four spaces: first stays normal, remaining three become &nbsp;
        assertTrue(html.contains("word1 &nbsp;&nbsp;&nbsp;word2"),
                "Multiple internal spaces should be preserved\n" + html);
    }

    @Test
    void userMessageEscapesHtml() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String text = "<script>alert('x')</script>";
        String html = HtmlContentPreparer.prepareHtml(text, theme, "user", false);

        assertFalse(html.contains("<script>"),
                "User messages should escape raw HTML tags\n" + html);
        assertTrue(html.contains("&lt;script&gt;"),
                "HTML metacharacters should be escaped in user messages\n" + html);
    }

    @Test
    void userMessageNormalizesWindowsLineEndings() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String text = "line1\r\nline2\rline3";
        String html = HtmlContentPreparer.prepareHtml(text, theme, "user", false);

        assertFalse(html.contains("\r"),
                "No raw \\r characters should remain in rendered user HTML\n" + html);
        assertTrue(html.contains("<p style='margin:0'>line1</p>")
                        && html.contains("<p style='margin:0'>line2</p>")
                        && html.contains("<p style='margin:0'>line3</p>"),
                "Windows/Mac line endings should normalize to separate <p> blocks\n" + html);
    }
}
