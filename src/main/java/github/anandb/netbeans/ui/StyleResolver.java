package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/**
 * Resolves markdown token types to {@link SimpleAttributeSet} styles.
 */
final class StyleResolver {

    private StyleResolver() {
    }

    /**
     * Creates the base text attributes for the given font and foreground color.
     */
    static SimpleAttributeSet baseStyle(Font font, Color foreground) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attr, font.getFamily());
        StyleConstants.setFontSize(attr, font.getSize() - 1);
        StyleConstants.setForeground(attr, foreground);
        StyleConstants.setSpaceAbove(attr, 4);
        StyleConstants.setSpaceBelow(attr, 4);
        return attr;
    }

    /**
     * Creates attributes for a header at the given level.
     */
    static SimpleAttributeSet headerStyle(SimpleAttributeSet base, Font baseFont, int level) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setBold(attr, true);
        int size = baseFont.getSize() + 1;
        if (level == 1) size += 6;
        else if (level == 2) size += 4;
        else if (level == 3) size += 2;
        StyleConstants.setFontSize(attr, size);
        StyleConstants.setSpaceAbove(attr, 8);
        StyleConstants.setSpaceBelow(attr, 4);
        return attr;
    }

    /**
     * Creates attributes for a code block.
     */
    static SimpleAttributeSet codeBlockStyle(Font baseFont, Color codeFg) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attr, "monospace");
        StyleConstants.setFontSize(attr, baseFont.getSize());
        StyleConstants.setForeground(attr, codeFg);
        StyleConstants.setSpaceAbove(attr, 8);
        StyleConstants.setSpaceBelow(attr, 8);
        return attr;
    }

    /**
     * Creates attributes for inline bold text.
     */
    static SimpleAttributeSet boldStyle(SimpleAttributeSet base) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setBold(attr, true);
        return attr;
    }

    /**
     * Creates attributes for inline italic text.
     */
    static SimpleAttributeSet italicStyle(SimpleAttributeSet base) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setItalic(attr, true);
        return attr;
    }

    /**
     * Creates attributes for inline strikethrough text.
     */
    static SimpleAttributeSet strikethroughStyle(SimpleAttributeSet base) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setStrikeThrough(attr, true);
        return attr;
    }

    /**
     * Creates attributes for inline code text.
     */
    static SimpleAttributeSet inlineCodeStyle(SimpleAttributeSet base, Font baseFont, Color codeFg) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setFontFamily(attr, "monospace");
        StyleConstants.setFontSize(attr, baseFont.getSize());
        StyleConstants.setForeground(attr, codeFg);
        return attr;
    }

    /**
     * Creates attributes for a blockquote paragraph.
     */
    static SimpleAttributeSet blockquoteStyle(SimpleAttributeSet base) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setItalic(attr, true);
        StyleConstants.setLeftIndent(attr, 16f);
        return attr;
    }

    /**
     * Creates attributes for table text.
     */
    static SimpleAttributeSet tableStyle(SimpleAttributeSet base, Color codeFg) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attr, "monospace");
        StyleConstants.setFontSize(attr, StyleConstants.getFontSize(base) - 1);
        StyleConstants.setForeground(attr, codeFg);
        StyleConstants.setSpaceAbove(attr, 4);
        StyleConstants.setSpaceBelow(attr, 4);
        return attr;
    }
}
