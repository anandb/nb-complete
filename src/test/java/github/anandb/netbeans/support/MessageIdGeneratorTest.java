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
}
