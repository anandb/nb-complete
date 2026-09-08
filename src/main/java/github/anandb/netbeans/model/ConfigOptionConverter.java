package github.anandb.netbeans.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts Claude's models/modes response format to SessionConfigOption format
 * for use by ConfigPanelController.
 */
public final class ConfigOptionConverter {

    private ConfigOptionConverter() {}

    /**
     * Converts models and modes from a Session into SessionConfigOption list.
     * Returns null if both models and modes are null.
     */
    public static List<SessionConfigOption> fromModelsAndModes(ModelsInfo models, ModesInfo modes) {
        if (models == null && modes == null) {
            return null;
        }

        List<SessionConfigOption> options = new ArrayList<>();

        if (models != null && models.availableModels() != null) {
            List<SessionConfigSelectOption> modelOptions = models.availableModels().stream()
                    .map(m -> new SessionConfigSelectOption(m.modelId(), m.name(), m.description()))
                    .toList();
            options.add(new SessionConfigOption(
                    "model",
                    "Model",
                    "Select the AI model",
                    "model",
                    "select",
                    models.currentModelId(),
                    modelOptions));
        }

        if (modes != null && modes.availableModes() != null) {
            List<SessionConfigSelectOption> modeOptions = modes.availableModes().stream()
                    .map(m -> new SessionConfigSelectOption(m.id(), m.name(), m.description()))
                    .toList();
            options.add(new SessionConfigOption(
                    "mode",
                    "Mode",
                    "Select the agent mode",
                    "mode",
                    "select",
                    modes.currentModeId(),
                    modeOptions));
        }

        return options.isEmpty() ? null : options;
    }
}
