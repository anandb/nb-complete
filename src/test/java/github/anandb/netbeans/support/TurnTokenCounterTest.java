package github.anandb.netbeans.support;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurnTokenCounterTest {

    @Test
    void streamedChunksOfOneMessageAccumulate() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.agent_message_chunk, "Hel", "same", null, null));
        counter.add(message(MessageType.agent_message_chunk, "lo world", "same", null, null));
        assertEquals(2, counter.count(), "A word split across chunks of one message must count once");
    }

    @Test
    void separateMessagesKeepTheirOwnWordBoundaries() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.agent_message_chunk, "Hel", "assistant", null, null));
        counter.add(message(MessageType.tool_call, "lo world", "tool", null, null));
        assertEquals(3, counter.count(), "Different messages must not merge their first and last words");
    }

    @Test
    void userMessagesAreIgnored() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.user_message_chunk, "hello world", "user", null, null));
        assertEquals(0, counter.count(), "User input is not part of the response token count");
    }

    @Test
    void ignoredToolStatusesAreNotCounted() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.tool_call, "running", "tool", null, "pending"));
        assertEquals(0, counter.count(), "The chat thread ignores pending tool status text");
    }

    @Test
    void resetClearsEveryMessageCounter() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.agent_message_chunk, "one two", "first", null, null));
        counter.reset();
        counter.add(message(MessageType.agent_message_chunk, "three", "first", null, null));
        assertEquals(1, counter.count(), "Reset must start a fresh turn with no old counters");
    }

    private static ProcessedMessage message(MessageType type, String text, String id,
            String toolTitle, String status) {
        return new ProcessedMessage.Builder()
                .messageType(type)
                .text(text)
                .messageId(id)
                .toolTitle(toolTitle)
                .rawText(text)
                .status(status)
                .build();
    }
}
