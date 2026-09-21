package github.anandb.netbeans.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    /**
     * Parses model variants from a model config option. A trailing path segment is
     * only treated as a thinking-level variant when {@code variantTokens} (the effort
     * values the server declares) contains it. Model ids that merely carry a
     * namespaced name — {@code lm-studio/qwen/qwen3.5-9b} — must keep their full id,
     * otherwise distinct models collapse into a bogus provider-only entry such as
     * {@code lm-studio/qwen}, hiding the real models and sending an invalid value.
     *
     * <p>When the server declares no effort values, nothing is collapsed: the variant
     * ids are then the only handle for choosing an effort level, so each stays an
     * individually selectable model and keeps its full display name.
     */
    void parseModelVariants(SessionConfigOption opt, Set<String> variantTokens) {
        this.currentConfigModelId = opt.currentValue();
        modelVariants.clear();
        for (SessionConfigSelectOption o : opt.options()) {
            String value = o.value();
            String name = o.name();
            String baseId = value;
            String variantName = "default";
            int lastSlash = value.lastIndexOf('/');
            // ≥3 path segments (provider/model/variant) with a server-declared effort tail.
            if (lastSlash > 0 && lastSlash < value.length() - 1 && value.indexOf('/') != lastSlash) {
                String tail = value.substring(lastSlash + 1);
                if (variantTokens.contains(tail.toLowerCase(Locale.ROOT))) {
                    baseId = value.substring(0, lastSlash);
                    variantName = tail;
                }
            }
            String displayName = name;
            // Strip a trailing "(…)" only for a recognised variant: the parenthetical
            // holds the effort level ("M (High)") and the collapsed base item shows the
            // stripped name. Uncollapsed entries keep the server's full name — there the
            // parenthetical distinguishes models ("GLM-5.3-Flash (2x usage)").
            int parenIdx = displayName.lastIndexOf("(");
            if (!"default".equals(variantName) && parenIdx > 0 && displayName.endsWith(")")) {
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
            // If the model already has variants that encode thinking level,
            // "default" would be meaningless — GPT models need a real level.
            if (modelVariants.isEmpty() && opt.options().stream().anyMatch(o -> "default".equalsIgnoreCase(o.value()))) {
                return sendAndReturn(opt, "default", currentId);
            }
        }

        if ("model".equals(opt.category())) {
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


}
