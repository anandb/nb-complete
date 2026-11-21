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
    void thoughtChunksCount() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.agent_thought_chunk, "thinking hard about it", "t1", null, null));
        assertEquals(4, counter.count(), "Thinking text is part of the progress read");
    }

    @Test
    void pendingToolBodyCountsTowardProgress() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.tool_call, "$ mvn verify", "tool1", "Bash", "pending"));
        assertEquals(3, counter.count(),
                "A running tool's command line must tick the progress read");
    }

    @Test
    void repeatedPendingToolBodyCountsOnce() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.tool_call, "$ mvn verify", "tool1", "Bash", "pending"));
        counter.add(message(MessageType.tool_call_update, "$ mvn verify", "tool1", "Bash", "pending"));
        assertEquals(3, counter.count(),
                "Status pings re-delivering an unchanged body must not inflate the count");
    }

    @Test
    void toolGrowthCountsOnlyTheNewWords() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.tool_call, "$ mvn verify", "tool1", "Bash", "pending"));
        counter.add(message(MessageType.tool_call_update, "$ mvn verify\nbuild output", "tool1", "Bash", "pending"));
        assertEquals(5, counter.count(),
                "Only the words the accumulated body gained are counted");
    }

    @Test
    void bareToolStatusStillTicksTheRead() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.tool_call, "running", "tool1", null, "pending"));
        assertEquals(1, counter.count(),
                "A bare status word is the tool's only progress text and is counted");
    }

    @Test
    void resetClearsEveryMessageCounter() {
        TurnTokenCounter counter = new TurnTokenCounter();
        counter.add(message(MessageType.agent_message_chunk, "one two", "first", null, null));
        counter.add(message(MessageType.tool_call, "$ mvn verify", "tool1", "Bash", "pending"));
        counter.reset();
        counter.add(message(MessageType.agent_message_chunk, "three", "first", null, null));
        counter.add(message(MessageType.tool_call_update, "$ mvn verify", "tool1", "Bash", "pending"));
        assertEquals(4, counter.count(), "Reset must start a fresh turn with no old counters");
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
