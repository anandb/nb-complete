package github.anandb.netbeans.manager.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.manager.ToolDataExtractor;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

@ServiceProvider(service = StrategyRegistry.class)
public class StrategyRegistry {
    private static final Logger LOG = new Logger(StrategyRegistry.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private final Map<String, DataExtractionStrategy> typeStrategies = new LinkedHashMap<>();
    private final DataExtractionStrategy defaultStrategy = new DefaultStrategy();

    public StrategyRegistry() {
        register(MessageType.agent_message_chunk, new AgentMessageChunkStrategy());
        register(MessageType.agent_thought_chunk, new AgentThoughtChunkStrategy());
        register(MessageType.user_message_chunk, new UserMessageChunkStrategy());
        register(MessageType.tool_call_update, new ToolCallUpdateStrategy());
        register(MessageType.tool_call, new ToolCallUpdateStrategy());
        register(MessageType.message, new MessageStrategy());
        register(MessageType.config_options_update, new ConfigOptionsUpdateStrategy());
        register(MessageType.session_info_update, new SessionInfoUpdateStrategy());
        register(MessageType.usage_update, new UsageUpdateStrategy());
        register(MessageType.plan, new PlanStrategy());
    }

    public static StrategyRegistry getInstance() {
        StrategyRegistry reg = Lookup.getDefault().lookup(StrategyRegistry.class);
        if (reg == null) {
            reg = new StrategyRegistry();
        }
        return reg;
    }

    public DataExtractionStrategy select(SessionUpdate update) {
        String type = update.type();
        if (type == null) {
            LOG.warn("Received a message with NULL type {0}", update);
            return defaultStrategy;
        }

        type = defaultIfBlank(reClassify(update), update.type());
        if (hasAnnotationFilter(update) && shouldSkip(update.content())) {
            LOG.fine("Strategy skipped for {0} due to annotation filter", type);
            return null;
        }

        DataExtractionStrategy selected = null;
        DataExtractionStrategy s = typeStrategies.get(type);
        if (s != null && s.canHandle(update, type)) {
            selected = s;
        }

        if (selected == null) {
            selected = defaultStrategy;
        }

        LOG.fine("Strategy selected for {0}: {1}", type, selected.getClass().getSimpleName());
        return selected;
    }

    public boolean hasAnnotationFilter(SessionUpdate update) {
        String t = update.type();
        return MessageType.agent_message_chunk.name().equals(t)
            || MessageType.agent_thought_chunk.name().equals(t)
            || MessageType.user_message_chunk.name().equals(t);
    }

    private boolean shouldSkip(JsonNode content) {
        if (content == null || !content.isObject() || !content.has("annotations")) return false;
        JsonNode annotations = content.get("annotations");
        if (annotations == null || !annotations.isObject()) return false;
        if (annotations.has("audience") && annotations.get("audience").isArray()) {
            for (JsonNode aud : annotations.get("audience")) {
                if ("assistant".equals(aud.asText())) return true;
            }
        }
        if (annotations.has("tags") && annotations.get("tags").isArray()) {
            for (JsonNode tag : annotations.get("tags")) {
                if ("hidden".equals(tag.asText())) return true;
            }
        }

        return false;
    }

    final void register(MessageType type, DataExtractionStrategy strategy) {
        typeStrategies.put(type.name(), strategy);
    }

    private String reClassify(SessionUpdate update) {
        String type = update.type();
        try {
            JsonNode content = update.content();
            String text = (content != null && content.isObject() && content.has("text"))
                    ? content.get("text").asText()
                    : MAPPER.writeValueAsString(content);
            var meta = ToolDataExtractor.classify(update.update().type(), text, update.kind(), update.update().title());
            if (meta.type() != update.update().type()) {
                type = meta.type().name();
            }
        } catch (JsonProcessingException e) {
            LOG.fine("Problem converting node to json {0}", update.messageId());
        }
        return type;
    }
}
