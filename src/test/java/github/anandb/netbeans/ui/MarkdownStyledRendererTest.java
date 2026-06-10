package github.anandb.netbeans.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarkdownStyledRendererTest {

    @BeforeAll
    static void setUp() {
        // Initialize UIManager defaults for the test environment to avoid potential NPEs
        UIManager.put("Panel.background", Color.BLACK);
        UIManager.put("Panel.foreground", Color.WHITE);
        UIManager.put("TextArea.selectionBackground", Color.BLUE);
        UIManager.put("Button.focusColor", Color.CYAN);
        UIManager.put("Label.background", Color.GRAY);
        UIManager.put("TextField.background", Color.LIGHT_GRAY);
        UIManager.put("TextArea.foreground", Color.WHITE);
        UIManager.put("Button.borderColor", Color.DARK_GRAY);
        UIManager.put("Button.background", Color.GRAY);
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("controlShadow", Color.BLACK);
        UIManager.put("control", Color.GRAY);
        UIManager.put("ComboBox.selectionBackground", Color.YELLOW);
        UIManager.put("OptionPane.background", Color.BLACK);
        UIManager.put("OptionPane.messageForeground", Color.WHITE);
        UIManager.put("TabbedPane.unselectedBackground", Color.GRAY);
        UIManager.put("Label.font", new Font("Dialog", Font.PLAIN, 12));
    }

    @Test
    void testHeadersWithSpace() throws Exception {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        JTextPane pane = MarkdownStyledRenderer.render("# Heading 1\n## Heading 2\n#NotAHeading", theme);
        StyledDocument doc = pane.getStyledDocument();
        String text = doc.getText(0, doc.getLength());

        // The text should contain "Heading 1", "Heading 2", and "#NotAHeading"
        assertTrue(text.contains("Heading 1"));
        assertTrue(text.contains("Heading 2"));
        assertTrue(text.contains("#NotAHeading"));

        // Verify style attributes
        int h1Index = text.indexOf("Heading 1");
        assertTrue(StyleConstants.isBold(doc.getCharacterElement(h1Index).getAttributes()));

        int notAHeadingIndex = text.indexOf("#NotAHeading");
        assertFalse(StyleConstants.isBold(doc.getCharacterElement(notAHeadingIndex).getAttributes()));
    }

    @Test
    void testUnclosedBoldAndItalic() throws Exception {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        JTextPane pane = MarkdownStyledRenderer.render("This is **bold and *italic*", theme);
        StyledDocument doc = pane.getStyledDocument();
        String text = doc.getText(0, doc.getLength());

        // With the fix, the orphaned bold marker is printed as "**" and does not trigger accidental italicization.
        int boldTextIdx = text.indexOf("bold and ");
        assertFalse(StyleConstants.isItalic(doc.getCharacterElement(boldTextIdx).getAttributes()));

        // The text "italic" should have italic styling
        int italicTextIdx = text.indexOf("italic");
        assertTrue(StyleConstants.isItalic(doc.getCharacterElement(italicTextIdx).getAttributes()));
    }

    @Test
    void testTableHeadersBoldingAndSeparatorRow() throws Exception {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String table = "| Col 1 | Col 2 |\n|---|---|\n| Val 1 | Val 2 |";
        JTextPane pane = MarkdownStyledRenderer.render(table, theme);
        StyledDocument doc = pane.getStyledDocument();
        String text = doc.getText(0, doc.getLength());

        // Separator row (dashes) should be skipped/omitted
        assertFalse(text.contains("---"));

        // Header row should be present and bolded
        int col1Idx = text.indexOf("Col 1");
        assertTrue(StyleConstants.isBold(doc.getCharacterElement(col1Idx).getAttributes()));

        // Data row should be present and NOT bolded
        int val1Idx = text.indexOf("Val 1");
        assertFalse(StyleConstants.isBold(doc.getCharacterElement(val1Idx).getAttributes()));
    }

    @Test
    void testEscapedFormatting() throws Exception {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        JTextPane pane = MarkdownStyledRenderer.render("This is \\*not italic\\* and this is \\| escaped pipe in text", theme);
        StyledDocument doc = pane.getStyledDocument();
        String text = doc.getText(0, doc.getLength());

        assertTrue(text.contains("*not italic*"));
        assertFalse(StyleConstants.isItalic(doc.getCharacterElement(text.indexOf("not italic")).getAttributes()));

        // Table with escaped pipe
        String table = "| Col 1 \\| escaped | Col 2 |\n|---|---|\n| Val 1 | Val 2 |";
        JTextPane tablePane = MarkdownStyledRenderer.render(table, theme);
        StyledDocument tableDoc = tablePane.getStyledDocument();
        String tableText = tableDoc.getText(0, tableDoc.getLength());

        assertTrue(tableText.contains("Col 1 | escaped"));
    }

    @Test
    void testStrikethrough() throws Exception {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        JTextPane pane = MarkdownStyledRenderer.render("This is ~~strike~~ text", theme);
        StyledDocument doc = pane.getStyledDocument();
        String text = doc.getText(0, doc.getLength());

        assertTrue(text.contains("strike"));
        int strikeIdx = text.indexOf("strike");
        assertTrue(StyleConstants.isStrikeThrough(doc.getCharacterElement(strikeIdx).getAttributes()));
    }

    @Test
    void testBlockquote() throws Exception {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        JTextPane pane = MarkdownStyledRenderer.render("> This is a blockquote", theme);
        StyledDocument doc = pane.getStyledDocument();
        String text = doc.getText(0, doc.getLength());

        assertTrue(text.contains("This is a blockquote"));
        int quoteIdx = text.indexOf("This is a blockquote");
        assertTrue(StyleConstants.isItalic(doc.getCharacterElement(quoteIdx).getAttributes()));

        float indent = StyleConstants.getLeftIndent(doc.getParagraphElement(quoteIdx).getAttributes());
        org.junit.jupiter.api.Assertions.assertEquals(16.0f, indent);
    }
}
