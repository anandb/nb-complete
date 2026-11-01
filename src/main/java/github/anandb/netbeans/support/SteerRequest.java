package github.anandb.netbeans.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the params of goose's {@code _goose/unstable/session/steer} extension, which adds
 * user input to the running turn instead of starting a new one.
 *
 * <p>goose rejects a second {@code session/prompt} while a run is active
 * ("session already has active run …; use _goose/unstable/session/steer"), and requires
 * {@code expectedRunId} to match that run, so the caller must supply the tracked run id.</p>
 */
public final class SteerRequest {

    /** goose-specific method that steers a running turn. */
    public static final String METHOD = "_goose/unstable/session/steer";

    private SteerRequest() {
    }

    /**
     * Builds the request params. The text becomes the first prompt block; attachment blocks
     * follow it. An empty text still produces a valid request when blocks are present.
     *
     * @param sessionId     the ACP session to steer
     * @param text          the user input, may be empty when {@code additionalBlocks} is not
     * @param additionalBlocks file/image blocks, may be {@code null}
     * @param expectedRunId the active run id reported by goose; required
     */
    public static Map<String, Object> params(String sessionId, String text,
                                             List<Map<String, Object>> additionalBlocks, String expectedRunId) {
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
        params.put("expectedRunId", expectedRunId);
        return params;
    }
}
