package github.anandb.netbeans.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 *
 * @author anand
 */
public final class MapperSupplier {

    private static final class Holder {
        private static final JsonMapper INSTANCE = JsonMapper.builder()
                .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS))
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
    }

    /**
     * Returns the singleton JsonMapper instance.
     *
     * @return the shared JsonMapper instance
     */
    public static JsonMapper get() {
        return Holder.INSTANCE;
    }
}
