package github.anandb.netbeans.ui;

import java.util.function.Consumer;

import javax.swing.Timer;

/**
 * Manages streaming lifecycle: active bubble reference and flush timer.
 * Extracted from ChatThreadPanel.
 */
// DSL-CONTROLLER: not a view — streamFlushTimer driver wrapping the per-bubble active stream. Stays imperative.
final class StreamingCoordinator {

    private MessageBubble activeStreamBubble;
    private final Timer streamFlushTimer;

    /**
     * @param onFlushTick   called with the active bubble on each timer tick (may be null)
     * @param flushIntervalMs timer interval in milliseconds
     */
    StreamingCoordinator(Consumer<MessageBubble> onFlushTick, int flushIntervalMs) {
        this.streamFlushTimer = new Timer(flushIntervalMs, e -> onFlushTick.accept(activeStreamBubble));
        this.streamFlushTimer.setRepeats(true);
    }

    /** Start streaming for the given bubble. */
    void startStreaming(MessageBubble bubble) {
        if (activeStreamBubble != null) {
            // Stop prior stream before overwriting, else the old bubble's
            // streaming JTextArea is never finalized.
            activeStreamBubble.flushUpdate(true);
            if (activeStreamBubble.streamingFlagsSet()) {
                activeStreamBubble.finalizeStreaming(true, true);
            } else if (activeStreamBubble.hasStreamingTextArea()) {
                activeStreamBubble.forceFinalize(true);
            }
        }
        activeStreamBubble = bubble;
        streamFlushTimer.start();
    }

    /** Stop the timer, clear the active bubble, and return it (or null). */
    MessageBubble stopStreaming() {
        streamFlushTimer.stop();
        MessageBubble bubble = activeStreamBubble;
        activeStreamBubble = null;
        return bubble;
    }

    /** Stop the timer and clear the active bubble without returning it. */
    void cleanup() {
        streamFlushTimer.stop();
        activeStreamBubble = null;
    }

    MessageBubble getActiveStreamBubble() {
        return activeStreamBubble;
    }
}
