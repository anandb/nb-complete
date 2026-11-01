package github.anandb.netbeans.support;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Payload shape of goose's {@code _goose/unstable/session/steer} request. */
class SteerRequestTest {

    @Test
    void textAndRunIdBecomeTheSteerPayload() {
        Map<String, Object> params = SteerRequest.params("s1", "hello", null, "run-1");

        assertEquals("s1", params.get("sessionId"));
        assertEquals("run-1", params.get("expectedRunId"));
        List<Map<String, Object>> prompt = prompt(params);
        assertEquals(1, prompt.size());
        assertEquals("text", prompt.get(0).get("type"));
        assertEquals("hello", prompt.get(0).get("text"));
    }

    @Test
    void attachmentBlocksFollowTheText() {
        Map<String, Object> image = Map.of("type", "image", "data", "AAAA");
        Map<String, Object> params = SteerRequest.params("s1", "look", List.of(image), "run-1");

        List<Map<String, Object>> prompt = prompt(params);
        assertEquals(2, prompt.size());
        assertEquals("look", prompt.get(0).get("text"));
        assertEquals(image, prompt.get(1));
    }

    @Test
    void emptyTextStillSendsTheAttachments() {
        Map<String, Object> file = Map.of("type", "resource_link", "uri", "file:///a.txt");
        List<Map<String, Object>> prompt = prompt(SteerRequest.params("s1", "", List.of(file), "run-1"));

        assertEquals(1, prompt.size(), "an empty text must not add an empty text block");
        assertEquals(file, prompt.get(0));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> prompt(Map<String, Object> params) {
        return (List<Map<String, Object>>) params.get("prompt");
    }
}
