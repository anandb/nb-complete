package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Resolves markdown token types to {@link SimpleAttributeSet} styles.
 *
 * <p>Style derivations are cached per (base, derived-type) keyed by base
 * identity. This avoids recreating a new SimpleAttributeSet for every inline
 * token when rendering large markdown.
 */
final class StyleResolver {

    private StyleResolver() {
    }

    /** Per-base derived-style cache. Keyed on base identity hash; values hold
     *  the resolved styles for that specific base. Caffeine handles concurrency
     *  and LRU eviction (max 32 entries). Cleared on theme switch. */
    private static final Cache<Integer, DerivedStyles> DERIVED_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(32)
                    .build();

    private static class DerivedStyles {
        /** The base instance this was derived from — used to detect
         *  identityHashCode collisions in the cache. */
        final SimpleAttributeSet base;
        final SimpleAttributeSet bold;
        final SimpleAttributeSet italic;
        final SimpleAttributeSet strike;
        final SimpleAttributeSet blockquote;
        final SimpleAttributeSet inlineCode;

        DerivedStyles(SimpleAttributeSet base, Font baseFont, Color codeFg) {
            this.base = base;
            this.bold = makeBold(base);
            this.italic = makeItalic(base);
            this.strike = makeStrike(base);
            this.blockquote = makeBlockquote(base);
            this.inlineCode = makeInlineCode(base, baseFont, codeFg);
        }
    }

    private static SimpleAttributeSet makeBold(SimpleAttributeSet base) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setBold(attr, true);
        return attr;
    }

    private static SimpleAttributeSet makeItalic(SimpleAttributeSet base) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setItalic(attr, true);
        return attr;
    }

    private static SimpleAttributeSet makeStrike(SimpleAttributeSet base) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setStrikeThrough(attr, true);
        return attr;
    }

    private static SimpleAttributeSet makeBlockquote(SimpleAttributeSet base) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setItalic(attr, true);
        StyleConstants.setLeftIndent(attr, 16f);
        return attr;
    }

    private static SimpleAttributeSet makeInlineCode(SimpleAttributeSet base, Font baseFont, Color codeFg) {
        SimpleAttributeSet attr = new SimpleAttributeSet(base);
        StyleConstants.setFontFamily(attr, "monospace");
        StyleConstants.setFontSize(attr, baseFont.getSize());
        StyleConstants.setForeground(attr, codeFg);
        return attr;
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

    /** Returns the cached derived styles for the given base. The base must
     *  have been created by {@link #baseStyle} (or by a previous derivation
     *  that shares identity with one). Identity-based caching means each
     *  render gets a fresh cache entry tied to its own base instance, which
     *  is cheap because typical renders only create one base. */
    private static DerivedStyles derivedFor(SimpleAttributeSet base, Font baseFont, Color codeFg) {
        Integer key = System.identityHashCode(base);
        DerivedStyles existing = DERIVED_CACHE.getIfPresent(key);
        // Verify the cached entry is actually for THIS base instance, not a
        // hash collision from a different SimpleAttributeSet instance.
        if (existing != null && existing.base == base) {
            return existing;
        }
        DerivedStyles created = new DerivedStyles(base, baseFont, codeFg);
        DERIVED_CACHE.put(key, created);
        return created;
    }

    /**
     * Creates attributes for inline bold text.
     */
    static SimpleAttributeSet boldStyle(SimpleAttributeSet base) {
        return boldStyle(base, null, null);
    }

    /** Variant of {@link #boldStyle} that participates in the derived cache
     *  only when baseFont and codeFg are non-null. The plain call site keeps
     *  its existing semantics; the renderer uses the parameterized form so
     *  one pass populates all derived styles for the base. */
    static SimpleAttributeSet boldStyle(SimpleAttributeSet base, Font baseFont, Color codeFg) {
        if (baseFont != null && codeFg != null) {
            return derivedFor(base, baseFont, codeFg).bold;
        }
        return makeBold(base);
    }

    /**
     * Creates attributes for inline italic text.
     */
    static SimpleAttributeSet italicStyle(SimpleAttributeSet base) {
        return italicStyle(base, null, null);
    }

    static SimpleAttributeSet italicStyle(SimpleAttributeSet base, Font baseFont, Color codeFg) {
        if (baseFont != null && codeFg != null) {
            return derivedFor(base, baseFont, codeFg).italic;
        }
        return makeItalic(base);
    }

    /**
     * Creates attributes for inline strikethrough text.
     */
    static SimpleAttributeSet strikethroughStyle(SimpleAttributeSet base) {
        return strikethroughStyle(base, null, null);
    }

    static SimpleAttributeSet strikethroughStyle(SimpleAttributeSet base, Font baseFont, Color codeFg) {
        if (baseFont != null && codeFg != null) {
            return derivedFor(base, baseFont, codeFg).strike;
        }
        return makeStrike(base);
    }

    /**
     * Creates attributes for inline code text.
     */
    static SimpleAttributeSet inlineCodeStyle(SimpleAttributeSet base, Font baseFont, Color codeFg) {
        return derivedFor(base, baseFont, codeFg).inlineCode;
    }

    /**
     * Creates attributes for a blockquote paragraph.
     */
    static SimpleAttributeSet blockquoteStyle(SimpleAttributeSet base) {
        return blockquoteStyle(base, null, null);
    }

    static SimpleAttributeSet blockquoteStyle(SimpleAttributeSet base, Font baseFont, Color codeFg) {
        if (baseFont != null && codeFg != null) {
            return derivedFor(base, baseFont, codeFg).blockquote;
        }
        return makeBlockquote(base);
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

    /** Clears the derived style cache. Call on theme switch. */
    static void clearCache() {
        DERIVED_CACHE.invalidateAll();
    }
}
