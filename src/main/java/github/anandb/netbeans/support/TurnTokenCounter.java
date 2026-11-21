package github.anandb.netbeans.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;

import static org.apache.commons.lang3.StringUtils.defaultString;

/**
 * Per-turn token estimate for the mini assistant.
 *
 * <p>Counts streamed response chunks incrementally instead of rescanning the
 * whole transcript on every update. Each message identity gets its own
 * {@link TokenCounter}, so a word split across two chunks of the same message
 * is counted once, while separate tool/assistant messages remain separate
 * counts. User messages are not counted.</p>
 *
 * <p>Tool streams re-deliver their accumulated body (command line plus output
 * so far) on every update, and a long-running tool spends most of its life in
 * a pending state the chat thread does not render as text. The read is a
 * progress indicator, so tool text is counted by the growth of the
 * accumulated body — including while pending — and an unchanged body adds
 * nothing.</p>
 */
public final class TurnTokenCounter {

    private final ConcurrentMap<String, TokenCounter> counters = new ConcurrentHashMap<>();
    /** Last accumulated body seen per tool message, for growth deltas. */
    private final ConcurrentMap<String, String> toolTexts = new ConcurrentHashMap<>();
    private int total;

    /** Adds one streamed or finalized response message. */
    public void add(ProcessedMessage message) {
        if (message == null) {
            return;
        }
        MessageType type = message.messageType();
        if (type == null || type.isUser()) {
            return;
        }
        if (type.isTool()) {
            addTool(message);
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

    /**
     * Counts one tool delivery as the growth of its accumulated body. The key
     * is the message id alone: {@code tool_call} and {@code tool_call_update}
     * share one id, and keying on the type would double-count one tool.
     */
    private void addTool(ProcessedMessage message) {
        String text = message.text();
        if (text == null || text.isEmpty()) {
            return;
        }
        String key = "TOOL|" + defaultString(message.messageId());
        String previous = toolTexts.put(key, text);
        int nowWords = TokenCounter.countWords(text);
        int previousWords = previous == null ? 0 : TokenCounter.countWords(previous);
        if (nowWords > previousWords) {
            total += nowWords - previousWords;
        }
    }

    /** Current token estimate for the turn. */
    public int count() {
        return total;
    }

    /** Clears every per-message counter and the running total. */
    public void reset() {
        counters.clear();
        toolTexts.clear();
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
