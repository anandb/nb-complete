package github.anandb.netbeans.support;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The {@code x.ai/interject} params shape: session, text, and the content blocks. */
class InterjectRequestTest {

    @Test
    void textAndBlocksBecomeContentBlocks() {
        List<Map<String, Object>> blocks = List.of(
                Map.of("type", "image", "data", "x", "mimeType", "image/png"));

        Map<String, Object> params = InterjectRequest.params("s1", "hello", blocks);

        assertEquals("s1", params.get("sessionId"));
        assertEquals("hello", params.get("text"));
        List<?> content = (List<?>) params.get("content");
        assertEquals(2, content.size(), "the text block, then the attachment block");
        assertEquals(Map.of("type", "text", "text", "hello"), content.get(0));
        assertEquals(blocks.get(0), content.get(1));
    }

    @Test
    void emptyTextWithNoBlocksOmitsContent() {
        Map<String, Object> params = InterjectRequest.params("s1", "", null);

        assertEquals("s1", params.get("sessionId"));
        assertEquals("", params.get("text"));
        assertFalse(params.containsKey("content"), "no blocks means the legacy text-only shape");
    }

    @Test
    void methodIsGrokInterject() {
        assertEquals("x.ai/interject", InterjectRequest.METHOD);
    }
}
