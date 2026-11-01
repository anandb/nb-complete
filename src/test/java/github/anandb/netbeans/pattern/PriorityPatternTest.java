package github.anandb.netbeans.pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PriorityPatternTest {
    @Test
    void acceptsSingleUppercase() {
        assertTrue(PriorityPattern.matches("A"));
        assertTrue(PriorityPattern.matches("Z"));
    }

    @Test
    void rejectsLowercaseAndMultiChar() {
        assertFalse(PriorityPattern.matches("a"));
        assertFalse(PriorityPattern.matches("AB"));
        assertFalse(PriorityPattern.matches(""));
        assertFalse(PriorityPattern.matches(null));
    }
}
