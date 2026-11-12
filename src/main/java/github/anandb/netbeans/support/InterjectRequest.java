package github.anandb.netbeans.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the params of Grok Build's {@code x.ai/interject} extension, which queues a
 * mid-turn interjection that the session actor drains into the running turn at the next
 * safe point. Unlike goose's steer, it needs no run id.
 *
 * <p>The text becomes the first content block; attachment blocks follow. The handler
 * prefers a text block in {@code content} over the legacy {@code text} field, so the
 * text is sent in both for compatibility.</p>
 */
public final class InterjectRequest {

    /** Grok-specific method that interjects into a running turn. */
    public static final String METHOD = "x.ai/interject";

    private InterjectRequest() {
    }

    /**
     * Builds the request params.
     *
     * @param sessionId        the ACP session to interject into
     * @param text             the user input, may be empty when {@code additionalBlocks} is not
     * @param additionalBlocks file/image content blocks, may be {@code null}
     */
    public static Map<String, Object> params(String sessionId, String text,
                                             List<Map<String, Object>> additionalBlocks) {
        String body = text == null ? "" : text;
        List<Map<String, Object>> content = new ArrayList<>();
        if (!body.isEmpty()) {
            content.add(Map.of("type", "text", "text", body));
        }
        if (additionalBlocks != null) {
            content.addAll(additionalBlocks);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sessionId", sessionId);
        params.put("text", body);
        if (!content.isEmpty()) {
            params.put("content", content);
        }
        return params;
    }
}
