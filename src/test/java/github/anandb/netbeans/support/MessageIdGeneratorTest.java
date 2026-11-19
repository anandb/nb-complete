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
                "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d",
                MessageIdGenerator.generate(null, null));
        assertEquals(MessageIdGenerator.generate(null, null),
                MessageIdGenerator.generate("", "   "));
    }

    @Test
    void knownVectorPinsTheHashConstruction() {
        assertEquals(
                "a6e19d7bd282da3b0760cacf8939e9089b7b54fa99e1474a4a4247cb4a179a54",
                MessageIdGenerator.generate("sess", "hello"));
    }
}
