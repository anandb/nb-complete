package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers both {@code models} shapes a {@code session/new} response can carry.
 * The object form is what Hermes sends and must not regress when the flat
 * pi-rust form is handled.
 */
class ModelsInfoDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ModelsInfo read(String json) throws Exception {
        return mapper.readValue("{\"models\":" + json + "}", Holder.class).models();
    }

    /** Minimal carrier, so the annotation on {@link ModelsInfo} is what is exercised. */
    record Holder(ModelsInfo models) {
    }

    @Test
    void objectShapeIsUnchanged() throws Exception {
        ModelsInfo info = read("{\"availableModels\":["
                + "{\"modelId\":\"hermes/one\",\"name\":\"One\",\"description\":\"first\"},"
                + "{\"modelId\":\"hermes/two\",\"name\":\"Two\"}],"
                + "\"currentModelId\":\"hermes/two\"}");

        assertNotNull(info);
        assertEquals(2, info.availableModels().size());
        assertEquals("hermes/one", info.availableModels().get(0).modelId());
        assertEquals("first", info.availableModels().get(0).description());
        assertEquals("hermes/two", info.currentModelId());
    }

    @Test
    void flatArrayBecomesModelsWithNoCurrentModel() throws Exception {
        ModelsInfo info = read("[{\"id\":\"claude-3\",\"name\":\"Claude\",\"provider\":\"bedrock\"},"
                + "{\"modelId\":\"gpt\",\"provider\":\"openai\"}]");

        assertNotNull(info);
        assertNull(info.currentModelId(), "a flat array carries no current model");
        assertEquals("bedrock/claude-3", info.availableModels().get(0).modelId());
        assertEquals("Claude", info.availableModels().get(0).name());
        assertEquals("openai/gpt", info.availableModels().get(1).modelId());
    }

    @Test
    void nameFallsBackToTheQualifiedId() throws Exception {
        ModelsInfo info = read("[{\"id\":\"m\",\"provider\":\"p\"}]");

        assertEquals("p/m", info.availableModels().get(0).name());
    }

    @Test
    void alreadyQualifiedIdIsNotPrefixedTwice() throws Exception {
        ModelsInfo info = read("[{\"id\":\"anthropic/claude\",\"provider\":\"bedrock\"}]");

        assertEquals("anthropic/claude", info.availableModels().get(0).modelId(),
                "an id carrying its own separator is already qualified");
    }

    @Test
    void entryWithoutIdIsSkippedWithoutLosingTheRest() throws Exception {
        ModelsInfo info = read("[{\"name\":\"no id\"},{\"id\":\"kept\"}]");

        assertEquals(1, info.availableModels().size());
        assertEquals("kept", info.availableModels().get(0).modelId());
    }

    @Test
    void emptyArrayYieldsAnEmptyList() throws Exception {
        ModelsInfo info = read("[]");

        assertNotNull(info);
        assertTrue(info.availableModels().isEmpty());
    }

    @Test
    void nullAvailableModelsDoesNotThrow() throws Exception {
        ModelsInfo info = read("{\"availableModels\":null,\"currentModelId\":\"m\"}");

        assertNotNull(info);
        assertNull(info.availableModels());
        assertEquals("m", info.currentModelId());
    }

    @Test
    void nonArrayAvailableModelsIsIgnoredInsteadOfAborting() throws Exception {
        ModelsInfo info = read("{\"availableModels\":\"oops\",\"currentModelId\":\"m\"}");

        assertNotNull(info, "an unexpected shape must not fail the whole Session");
        assertNull(info.availableModels());
        assertEquals("m", info.currentModelId());
    }

    @Test
    void absentModelsFieldYieldsNull() throws Exception {
        ModelsInfo info = read("{\"currentModelId\":\"m\"}");

        assertNotNull(info);
        assertNull(info.availableModels());
        assertEquals("m", info.currentModelId());
    }
}
