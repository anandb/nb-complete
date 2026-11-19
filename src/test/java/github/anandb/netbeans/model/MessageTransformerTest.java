package github.anandb.netbeans.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageTransformerTest {

    private final MessageTransformer transformer = new MessageTransformer();

    private static Message.ContentPart textPart(String text) {
        return new Message.ContentPart("text", text, null, null, null, null,
                null, null, null, null);
    }

    @Test
    void userTextAndVisiblePartsAreJoinedWithSingleNewlines() {
        Message message = new Message("u1", "user",
                new Message.Prompt("prompt", List.of(textPart("one"), textPart("two"))),
                null, null);

        ProcessedMessage processed = transformer.convert(message);

        assertEquals(MessageType.user_message_chunk, processed.messageType());
        assertEquals("prompt\none\ntwo", processed.text());
        assertEquals("prompt\none\ntwo", processed.rawText());
        assertEquals("u1", processed.messageId());
    }

    @Test
    void emptyAndNullPartsAreSkipped() {
        Message message = new Message("u2", "user",
                new Message.Prompt(null, List.of(textPart(""), textPart(null), textPart("only"))),
                null, null);

        assertEquals("only", transformer.convert(message).text());
    }

    @Test
    void assistantTextAndPartsUseBlankLineSeparation() {
        Message message = new Message("a1", "assistant", null,
                new Message.Completion("answer", List.of(textPart("one"), textPart("two")), null, null),
                null);

        ProcessedMessage processed = transformer.convert(message);

        assertEquals(MessageType.agent_message_chunk, processed.messageType());
        assertEquals("answer\n\none\n\ntwo", processed.text());
    }

    @Test
    void thinkingStateBecomesThoughtChunk() {
        Message message = new Message("t1", "assistant", null,
                new Message.Completion("reasoning", null, null, null), "thinking");

        ProcessedMessage processed = transformer.convert(message);

        assertEquals(MessageType.agent_thought_chunk, processed.messageType());
        assertEquals("reasoning", processed.text());
    }

    @Test
    void missingContentProducesEmptyText() {
        Message message = new Message("n1", "assistant", null, null, null);

        ProcessedMessage processed = transformer.convert(message);

        assertEquals("", processed.text());
        assertEquals("", processed.rawText());
    }
}
