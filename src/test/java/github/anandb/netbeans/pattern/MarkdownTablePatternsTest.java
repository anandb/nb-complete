package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkdownTablePatternsTest {
    @Test
    void separatorCells() {
        assertTrue(TableSeparatorPattern.matches("---"));
        assertTrue(TableSeparatorPattern.matches(":--:"));
        assertFalse(TableSeparatorPattern.matches("abc"));
    }

    @Test
    void splitsOnUnescapedPipe() {
        assertArrayEquals(new String[] {"a", "b"}, TableCellSplitPattern.split("a|b", -1));
        assertArrayEquals(new String[] {"a\\|b"}, TableCellSplitPattern.split("a\\|b", -1));
    }
}
