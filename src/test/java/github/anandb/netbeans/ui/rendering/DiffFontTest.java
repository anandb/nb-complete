package github.anandb.netbeans.ui.rendering;

import java.awt.Font;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The diff font is derived from the editor font rather than set by the user, so the derivation is
 * what has to stay true: a couple of points smaller, and never below the readability floor.
 */
class DiffFontTest {

    @Test
    void diffFontRunsTwoPointsBelowTheEditorFont() {
        Font base = IconResourceManager.getMonospaceFont();
        Font diff = IconResourceManager.getDiffMonospaceFont();

        // Both read the same cached base, so this holds whatever the IDE font happens to be.
        assertEquals(Math.max(9f, base.getSize() - 1f), diff.getSize());
        assertTrue(diff.getSize() <= base.getSize(), "the diff font must never grow past the editor font");
        assertEquals(base.getFamily(), diff.getFamily(), "only the size changes, not the family");
    }
}
