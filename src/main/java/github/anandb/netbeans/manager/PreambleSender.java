package github.anandb.netbeans.manager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Sends the startup preamble (critical rules, then the user preamble) for a new
 * session. Extracted from SessionManager; the prompt transport is injected so the
 * facade keeps its assistant-only prompt sender.
 */
final class PreambleSender {

    private static final Logger LOG = Logger.from(PreambleSender.class);

    /** Sends one assistant-only {@code session/prompt} and reports internal-message state. */
    @FunctionalInterface
    interface PromptSender {
        CompletableFuture<?> send(String sessionId, String text, String label);
    }

    private final SessionEventBus events;
    private final PromptSender promptSender;
    private volatile BiFunction<String, List<SessionConfigOption>, CompletableFuture<Void>> beforeHandler;

    PreambleSender(SessionEventBus events, PromptSender promptSender) {
        this.events = events;
        this.promptSender = promptSender;
    }

    void setBeforeHandler(
            BiFunction<String, List<SessionConfigOption>, CompletableFuture<Void>> handler) {
        this.beforeHandler = handler;
    }

    /**
     * Runs the optional pre-preamble handler (config dialog), then sends the preamble.
     * A skipped or empty preamble still reports done so the loading indicator clears.
     */
    void sendForNewSession(String sessionId, List<SessionConfigOption> options) {
        BiFunction<String, List<SessionConfigOption>, CompletableFuture<Void>> handler = beforeHandler;
        if (handler != null) {
            handler.apply(sessionId, options).whenComplete((v, ex) -> sendOrSkip(sessionId));
        } else {
            sendOrSkip(sessionId);
        }
    }

    private void sendOrSkip(String sessionId) {
        if (!send(sessionId)) {
            events.preambleSkipped(sessionId);
            events.preambleDone();
        }
    }

    /** @return true when a prompt was sent; false when the toggle is off or the text is empty */
    private boolean send(String sessionId) {
        String[] chunks = preambleChunks();
        if (chunks == null || chunks.length == 0) {
            return false;
        }
        events.preambleStarted(sessionId);
        sendChunk(sessionId, chunks, 0);
        return true;
    }

    /**
     * Builds the startup prompt chunks (critical rules, then the user preamble) or
     * returns {@code null} when the Enable Session Preamble checkbox is off or the
     * combined text is empty. The checkbox gates the whole prompt: no critical rules,
     * no WSL block, no user preamble. Chunks are split on {@code "\n---\n"}.
     */
    static String[] preambleChunks() {
        if (!PluginSettings.isPreambleEnabled()) {
            return null;
        }
        String rules = PluginSettings.getCriticalRules();
        String preamble = PluginSettings.getPreamble();

        StringBuilder combined = new StringBuilder();
        if (!isBlank(rules)) {
            combined.append(rules);
        }
        if (!isBlank(preamble)) {
            if (!combined.isEmpty()) {
                combined.append("\n\n");
            }
            combined.append(preamble);
        }

        String text = combined.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return StringUtils.splitByWholeSeparator(text, "\n---\n");
    }

    /** Sends preamble chunks sequentially, one at a time. */
    private void sendChunk(String sessionId, String[] chunks, int index) {
        if (index >= chunks.length) {
            events.preambleDone();
            return;
        }

        String chunk = chunks[index].trim();
        if (chunk.isEmpty()) {
            sendChunk(sessionId, chunks, index + 1);
            return;
        }

        String label = "preamble chunk " + (index + 1) + "/" + chunks.length;
        promptSender.send(sessionId, chunk, label)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        LOG.warn("Failed to send {0}: {1}", label, ExceptionUtils.getRootCauseMessage(ex));
                    }
                    // Continue with the next chunk even if this one failed.
                    sendChunk(sessionId, chunks, index + 1);
                });
    }
}
