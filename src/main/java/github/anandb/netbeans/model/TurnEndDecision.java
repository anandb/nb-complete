package github.anandb.netbeans.model;

/**
 * Pure end-of-turn decision for a {@code session/update} type string.
 *
 * <p>Extracted from {@code SessionLifecycleHandler.onSessionUpdate} so the decision is
 * testable headless without any UI dispatch. Two independent outcomes:</p>
 * <ul>
 *   <li>{@code endOfTurn} — {@code responding_finished}/{@code end_turn} end the turn for
 *       every agent.</li>
 *   <li>{@code preambleReady} — {@code available_commands_update} may clear the pending
 *       preamble wait early for interleaved agents only. It is deliberately NOT an
 *       end-of-turn signal: queueing agents (goose) emit it at the START of a turn, and
 *       treating it as turn-end sets turnEnded mid-stream — the next user message then
 *       bypasses the queue guard and hits goose while the first prompt is still in
 *       flight, which drops the in-flight prompt and wedges the session.</li>
 * </ul>
 */
public record TurnEndDecision(boolean endOfTurn, boolean preambleReady) {

    /**
     * @param type          the raw {@code sessionUpdate} type name, or {@code null}
     * @param queueingAgent whether the harness drops in-flight prompts (goose-style)
     */
    public static TurnEndDecision of(String type, boolean queueingAgent) {
        MessageType mt;
        try {
            mt = type != null ? MessageType.valueOf(type) : null;
        } catch (IllegalArgumentException unknown) {
            mt = null;
        }
        return of(mt, queueingAgent);
    }

    /** Type-safe overload; prefer this when a {@link MessageType} is already available. */
    public static TurnEndDecision of(MessageType type, boolean queueingAgent) {
        boolean endOfTurn = type == MessageType.responding_finished || type == MessageType.end_turn;
        boolean preambleReady = type == MessageType.available_commands_update && !queueingAgent;
        return new TurnEndDecision(endOfTurn, preambleReady);
    }
}
