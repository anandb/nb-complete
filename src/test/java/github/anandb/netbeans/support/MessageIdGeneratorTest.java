package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MessageIdGeneratorTest {

    @Test
    void trailingWhitespaceDoesNotChangeId() {
        String id = MessageIdGenerator.generate("sess", "hello\n");
        assertEquals(id, MessageIdGenerator.generate("sess", "hello"));
        assertEquals(id, MessageIdGenerator.generate("sess", "  hello\n\n"));
    }

    @Test
    void differentBodiesProduceDifferentIds() {
        assertNotEquals(
                MessageIdGenerator.generate("sess", "hello"),
                MessageIdGenerator.generate("sess", "hello!"));
    }

    @Test
    void sessionIdIsPartOfHash() {
        assertNotEquals(
                MessageIdGenerator.generate("s1", "hello"),
                MessageIdGenerator.generate("s2", "hello"));
    }

    @Test
    void nullInputsHashLikeEmptyStrings() {
        assertEquals(
                "e934a84adb052768",
                MessageIdGenerator.generate(null, null));
        assertEquals(MessageIdGenerator.generate(null, null),
                MessageIdGenerator.generate("", "   "));
    }

    @Test
    void knownVectorPinsTheHashConstruction() {
        assertEquals(
                "a50561bb7f1b00ec",
                MessageIdGenerator.generate("sess", "hello"));
    }
}
