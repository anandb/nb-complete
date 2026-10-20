package github.anandb.netbeans.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the two observed {@code models} shapes in a {@code session/new} /
 * {@code session/load} response:
 *
 * <ul>
 *   <li>object: {@code {"availableModels": [...], "currentModelId": "..."}} (Hermes)</li>
 *   <li>flat array: {@code [{"id": "...", "name": "...", "provider": "..."}]} (pi-rust)</li>
 * </ul>
 *
 * <p>The array form carries no current model, so {@code currentModelId} stays
 * null and the harness-provided {@code configOptions} remain the source of
 * that value. Without this, a flat array aborts the whole {@code Session}
 * deserialization (array vs object mismatch) and {@code session/new} fails.</p>
 */
public final class ModelsInfoDeserializer extends StdDeserializer<ModelsInfo> {

    private static final TypeReference<List<AvailableModel>> MODEL_LIST = new TypeReference<>() {};

    public ModelsInfoDeserializer() {
        super(ModelsInfo.class);
    }

    @Override
    public ModelsInfo deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.readValueAsTree();
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return new ModelsInfo(flatModels(node), null);
        }
        List<AvailableModel> available = node.has("availableModels")
                ? ctxt.readTreeAsValue(node.get("availableModels"),
                        ctxt.getTypeFactory().constructType(MODEL_LIST))
                : null;
        String current = node.hasNonNull("currentModelId") ? node.get("currentModelId").asText() : null;
        return new ModelsInfo(available, current);
    }

    private static List<AvailableModel> flatModels(JsonNode array) {
        List<AvailableModel> models = new ArrayList<>(array.size());
        for (JsonNode entry : array) {
            String id = text(entry, "modelId");
            if (id == null) {
                id = text(entry, "id");
            }
            if (id == null) {
                continue;
            }
            String provider = text(entry, "provider");
            // pi-rust reports the provider separately, while every value it
            // accepts (see configOptions in the same response) is provider/id.
            String value = provider != null && !provider.isBlank() && !id.startsWith(provider + "/")
                    ? provider + "/" + id
                    : id;
            String name = text(entry, "name");
            models.add(new AvailableModel(value, name != null ? name : value, text(entry, "description")));
        }
        return models;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isValueNode() && !value.isNull() ? value.asText() : null;
    }
}
