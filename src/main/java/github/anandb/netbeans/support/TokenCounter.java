package github.anandb.netbeans.support;

/**
 * Incremental whitespace-token counter for a single streamed message.
 *
 * <p>A message can arrive in chunks that split a word in two ({@code "Hel"}
 * followed by {@code "lo world"}). Counting each chunk independently would
 * count that first word twice, so the counter remembers whether the previous
 * chunk ended inside a word and subtracts the continuation from the next
 * chunk.</p>
 */
public final class TokenCounter {

    private int words;
    private boolean pendingWord;

    /** Adds a chunk and returns how many new words it contributed. */
    public int add(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return 0;
        }
        int delta = countWords(chunk);
        if (pendingWord && !Character.isWhitespace(chunk.charAt(0))) {
            delta--;
        }
        if (delta < 0) {
            delta = 0;
        }
        words += delta;
        pendingWord = !Character.isWhitespace(chunk.charAt(chunk.length() - 1));
        return delta;
    }

    /** Current word count. */
    public int count() {
        return words;
    }

    /** Clears the count and the pending-word state. */
    public void reset() {
        words = 0;
        pendingWord = false;
    }

    /** Counts whitespace-separated runs without allocating a word array. */
    static int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                inWord = false;
            } else if (!inWord) {
                count++;
                inWord = true;
            }
        }
        return count;
    }
}
