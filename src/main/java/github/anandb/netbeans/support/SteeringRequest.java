package github.anandb.netbeans.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the params of, and reads the reply of, the cross-adapter {@code _session/steering}
 * extension, which adds user input to a running turn instead of starting a second
 * {@code session/prompt}.
 *
 * <p>An agent advertises the extension at the top level of its initialize response
 * ({@code _meta.steering.supported}), a sibling of {@code agentCapabilities}. The request
 * carries the same content blocks as {@code session/prompt}; the reply's {@code outcome}
 * reports whether the message joined the running turn ({@code injected}) or the turn had
 * already ended ({@code promptRequired}, {@code startedNewTurn}).</p>
 */
public final class SteeringRequest {

    /** Extension method that injects input into a running turn. */
    public static final String METHOD = "_session/steering";

    /** Outcome that means the message joined the running turn. */
    public static final String OUTCOME_INJECTED = "injected";

    /** Outcome that means the turn this steer targeted had already ended. */
    public static final String OUTCOME_PROMPT_REQUIRED = "promptRequired";

    /** Compatibility outcome: the adapter started a detached turn instead of steering. */
    public static final String OUTCOME_STARTED_NEW_TURN = "startedNewTurn";

    /** {@code _meta.steering.idleBehavior} that asks for a refusal instead of a new turn. */
    public static final String IDLE_BEHAVIOR_PROMPT_REQUIRED = "promptRequired";

    private SteeringRequest() {
    }

    /**
     * Builds the request params. The text becomes the first prompt block; attachment blocks
     * follow it. An empty text still produces a valid request when blocks are present.
     *
     * <p>{@code _meta.steering.idleBehavior = promptRequired} keeps a race honest: an adapter
     * that supports it (claude-agent-acp, @gugu-acp/pi-acp) refuses the steer once the turn has
     * ended instead of starting a detached turn behind the client's back.</p>
     *
     * @param sessionId        the ACP session to steer
     * @param text             the user input, may be empty when {@code additionalBlocks} is not
     * @param additionalBlocks file/image blocks, may be {@code null}
     */
    public static Map<String, Object> params(String sessionId, String text,
                                             List<Map<String, Object>> additionalBlocks) {
        List<Map<String, Object>> prompt = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            prompt.add(Map.of("type", "text", "text", text));
        }
        if (additionalBlocks != null) {
            prompt.addAll(additionalBlocks);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sessionId", sessionId);
        params.put("prompt", prompt);
        params.put("_meta", Map.of("steering", Map.of("idleBehavior", IDLE_BEHAVIOR_PROMPT_REQUIRED)));
        return params;
    }

    /** The reply's {@code outcome}, or {@code null} when the reply carried none. */
    public static String outcome(JsonNode result) {
        JsonNode value = result == null ? null : result.get("outcome");
        return value != null && value.isTextual() ? value.asText() : null;
    }

    /** True only when the message actually joined the running turn. */
    public static boolean isInjected(JsonNode result) {
        return OUTCOME_INJECTED.equals(outcome(result));
    }

    /**
     * True when the reply means the turn had already ended rather than a transport or delivery
     * failure: {@code promptRequired} or the legacy {@code startedNewTurn}. Any other outcome
     * (omp answers {@code failed}) is an error the caller must surface, not a benign miss.
     */
    public static boolean isTurnGone(JsonNode result) {
        String value = outcome(result);
        return OUTCOME_PROMPT_REQUIRED.equals(value) || OUTCOME_STARTED_NEW_TURN.equals(value);
    }
}
