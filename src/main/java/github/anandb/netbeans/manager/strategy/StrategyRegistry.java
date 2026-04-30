package github.anandb.netbeans.manager.strategy;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

public class StrategyRegistry {
    private static final Logger LOG = new Logger(StrategyRegistry.class);
    private static final StrategyRegistry INSTANCE = new StrategyRegistry();

    private final Map<String, DataExtractionStrategy> typeStrategies = new LinkedHashMap<>();
    private final DataExtractionStrategy[] fallbackStrategies;
    private final DataExtractionStrategy defaultStrategy = new DefaultStrategy();

    private StrategyRegistry() {
        register("agent_message_chunk", new AgentMessageChunkStrategy());
        register("agent_thought_chunk", new AgentThoughtChunkStrategy());
        register("user_message_chunk", new UserMessageChunkStrategy());
        register("tool_call_update", new ToolCallUpdateStrategy());
        register("message", new MessageStrategy());
        register("agent_data_chunk", new DataChunkReclassifyStrategy());
        register("config_options_update", new ConfigOptionsUpdateStrategy());
        register("session_info_update", new SessionInfoUpdateStrategy());
        register("usage_update", new UsageUpdateStrategy());

        fallbackStrategies = new DataExtractionStrategy[] {
            new DataChunkReclassifyStrategy()
        };
    }

    public static StrategyRegistry getInstance() {
        return INSTANCE;
    }

    public void register(String type, DataExtractionStrategy strategy) {
        typeStrategies.put(type, strategy);
    }

    public DataExtractionStrategy select(SessionUpdate update) {
        String type = update.type();
        if (type == null) {
            return null;
        }

        DataExtractionStrategy selected = null;

        // 1. Try exact match
        DataExtractionStrategy s = typeStrategies.get(type);
        if (s != null) {
            if (hasAnnotationFilter(update) && shouldSkip(update.content())) {
                LOG.fine("Strategy skipped for {0} due to annotation filter", type);
                return null;
            }
            selected = s;
        }

        // 2. Try fallbacks (strategies that can handle multiple or reclassified types)
        if (selected == null) {
            for (DataExtractionStrategy fs : fallbackStrategies) {
                if (fs.canHandle(update)) {
                    selected = fs;
                    break;
                }
            }
        }

        // 3. Final default (logs unknown types)
        if (selected == null) {
            selected = defaultStrategy;
        }

        LOG.fine("Strategy selected for {0}: {1}", type, selected.getClass().getSimpleName());
        return selected;
    }


    public boolean hasAnnotationFilter(SessionUpdate update) {
        String t = update.type();
        return "agent_message_chunk".equals(t)
            || "agent_thought_chunk".equals(t)
            || "user_message_chunk".equals(t);
    }

    private boolean shouldSkip(JsonNode content) {
        if (content == null || !content.has("annotations")) return false;
        JsonNode annotations = content.get("annotations");
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
}
