package github.anandb.netbeans.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;

/**
 * Per-turn token estimate for the mini assistant.
 *
 * <p>Counts streamed response chunks incrementally instead of rescanning the
 * whole transcript on every update. Each message identity gets its own
 * {@link TokenCounter}, so a word split across two chunks of the same message
 * is counted once, while separate tool/assistant messages remain separate
 * counts. User messages and updates the chat thread would ignore are not
 * counted.</p>
 */
public final class TurnTokenCounter {

    private final ConcurrentMap<String, TokenCounter> counters = new ConcurrentHashMap<>();
    private int total;

    /** Adds one streamed or finalized response message. */
    public void add(ProcessedMessage message) {
        MessageType type = message == null ? null : message.messageType();
        if (type == null || type.isUser()) {
            return;
        }
        if (message.isIgnorable()) {
            return;
        }
        String text = message.text();
        if (text == null || text.isEmpty()) {
            return;
        }
        TokenCounter counter = counters.computeIfAbsent(
                key(type, message.messageId(), message.toolTitle()),
                key -> new TokenCounter());
        total += counter.add(text);
    }

    /** Current token estimate for the turn. */
    public int count() {
        return total;
    }

    /** Clears every per-message counter and the running total. */
    public void reset() {
        counters.clear();
        total = 0;
    }

    /**
     * Message identity for token counting. The type keeps an assistant stream
     * separate from a tool stream; the id and tool title follow the broad merge
     * rules the chat thread uses for streams that may lack a stable id.
     */
    private static String key(MessageType type, String messageId, String toolTitle) {
        return type.name() + '|' + (messageId == null ? "" : messageId)
                + '|' + (toolTitle == null ? "" : toolTitle);
    }
}
