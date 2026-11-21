package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenCounterTest {

    @Test
    void wordSplitAcrossChunksCountsOnce() {
        TokenCounter counter = new TokenCounter();
        counter.add("Hel");
        counter.add("lo world");
        assertEquals(2, counter.count(), "'Hello world' must count as two words, not three");
    }

    @Test
    void whitespaceChunkClosesThePendingWord() {
        TokenCounter counter = new TokenCounter();
        counter.add("Hel");
        counter.add(" ");
        counter.add("lo");
        assertEquals(2, counter.count(), "The whitespace separates 'Hel' and 'lo'");
    }

    @Test
    void emptyChunkKeepsThePendingWordState() {
        TokenCounter counter = new TokenCounter();
        counter.add("Hel");
        counter.add("");
        counter.add("lo");
        assertEquals(1, counter.count(), "'Hello' split by an empty chunk is still one word");
    }

    @Test
    void punctuationStillBelongsToTheCurrentWord() {
        TokenCounter counter = new TokenCounter();
        counter.add("Hel");
        counter.add("lo!");
        counter.add(" world");
        assertEquals(2, counter.count(), "Punctuation must not split a word");
    }

    @Test
    void resetClearsCountAndPendingState() {
        TokenCounter counter = new TokenCounter();
        counter.add("Hel");
        counter.reset();
        counter.add("lo world");
        assertEquals(2, counter.count(), "After reset, 'lo world' must not continue the old word");
    }
}
