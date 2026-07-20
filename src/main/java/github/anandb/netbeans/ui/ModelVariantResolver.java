package github.anandb.netbeans.ui;

import static org.apache.commons.lang3.StringUtils.split;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import github.anandb.netbeans.model.ModelRecords.ConfigItem;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionConfigSelectOption;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.SessionService;

/**
 * Resolves model variants and startup default values for config combos.
 * Extracted from ConfigPanelController to isolate model-parsing logic.
 */
final class ModelVariantResolver {

    private static final Logger LOG = Logger.from(ModelVariantResolver.class);

    private final SessionService sessionService = PlatformBridge.sessionServiceSafe();

    private final LinkedHashMap<String, List<ConfigItem>> modelVariants = new LinkedHashMap<>();
    private String currentConfigModelId = null;
    private String lastSelectedModelId;

    ModelVariantResolver() {
    }

    /** Parses model variants from a model config option. */
    void parseModelVariants(SessionConfigOption opt) {
        this.currentConfigModelId = opt.currentValue();
        modelVariants.clear();
        for (SessionConfigSelectOption o : opt.options()) {
            String value = o.value();
            String name = o.name();
            String[] segments = split(value, '/');
            String baseId;
            String variantName;
            if (segments.length >= 3) {
                baseId = String.join("/", Arrays.copyOfRange(segments, 0, segments.length - 1));
                variantName = segments[segments.length - 1];
            } else {
                baseId = value;
                variantName = "default";
            }
            String displayName = name;
            int parenIdx = displayName.lastIndexOf("(");
            if (parenIdx > 0 && displayName.endsWith(")")) {
                displayName = displayName.substring(0, parenIdx).trim();
            }
            modelVariants.computeIfAbsent(baseId, k -> new ArrayList<>())
                        .add(new ConfigItem(variantName, value, displayName));
        }
    }

    /** Returns the parsed model variants map. */
    LinkedHashMap<String, List<ConfigItem>> getModelVariants() {
        return modelVariants;
    }

    /** Returns the current config model ID. */
    String getCurrentConfigModelId() {
        return currentConfigModelId;
    }

    /** Returns the last selected model ID. */
    String getLastSelectedModelId() {
        return lastSelectedModelId;
    }

    /** Sets the last selected model ID. */
    void setLastSelectedModelId(String id) {
        this.lastSelectedModelId = id;
    }

    /**
     * Resolves the startup value for a config option based on environment
     * variables, force flags, and previously selected values.
     */
    String resolveStartupValue(SessionConfigOption opt, boolean isThinking,
                                String currentValue, boolean force) {
        if (!force) return currentValue;
        String currentId = sessionService.get().getCurrentSessionId();

        if ("mode".equals(opt.category())) {
            if (opt.options().stream().anyMatch(o -> "build".equalsIgnoreCase(o.value()))) {
                return sendAndReturn(opt, "build", currentId);
            }
            if (opt.options().stream().anyMatch(o -> "plan".equalsIgnoreCase(o.value()))) {
                return sendAndReturn(opt, "plan", currentId);
            }
        }

        if (isThinking) {
            if (opt.options().stream().anyMatch(o -> "default".equalsIgnoreCase(o.value()))) {
                return sendAndReturn(opt, "default", currentId);
            }
        }

        if ("model".equals(opt.category())) {
            String envModel = System.getenv("OPENCODE_MODEL");
            if (envModel != null && !envModel.isEmpty() && currentId != null) {
                String match = findModelMatch(opt, envModel);
                if (match != null) {
                    LOG.fine("Using OPENCODE_MODEL: {0}", new Object[]{match});
                    sessionService.get().setSessionConfigOption(currentId, opt.id(), match);
                    return match;
                }
            }
            if (lastSelectedModelId != null && !lastSelectedModelId.equalsIgnoreCase(currentValue)) {
                sessionService.get().setSessionConfigOption(currentId, opt.id(), lastSelectedModelId);
                return lastSelectedModelId;
            }
        }

        return currentValue;
    }

    private String sendAndReturn(SessionConfigOption opt, String forcedValue, String currentId) {
        if (!forcedValue.equalsIgnoreCase(opt.currentValue()) && currentId != null) {
            LOG.fine("Forcing default: {0}={1} (was {2})", new Object[]{opt.id(), forcedValue, opt.currentValue()});
            sessionService.get().setSessionConfigOption(currentId, opt.id(), forcedValue);
            return forcedValue;
        }
        return opt.currentValue();
    }

    private String findModelMatch(SessionConfigOption opt, String envModel) {
        for (SessionConfigSelectOption o : opt.options()) {
            if (o.value().equalsIgnoreCase(envModel)) {
                return o.value();
            }
        }
        for (Map.Entry<String, List<ConfigItem>> entry : modelVariants.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(envModel)) {
                return entry.getValue().get(0).value();
            }
        }
        return null;
    }
}
