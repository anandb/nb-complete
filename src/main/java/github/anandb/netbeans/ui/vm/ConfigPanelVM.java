package github.anandb.netbeans.ui.vm;

import java.util.List;

import github.anandb.netbeans.model.SessionConfigSelectOption;

/**
 * Swing-free view-model for the agent/model/thinking config dropdown panel.
 * Mirrors the mutable {@code JComboBox<ConfigItem>} state on
 * {@code ConfigPanelController} (mode/model/thinking selections + available
 * option lists).
 * <p>
 * <b>DSL-ready:</b> immutable record + withers, Swing-free.
 */
public record ConfigPanelVM(
        List<SessionConfigSelectOption> modeOptions,
        List<SessionConfigSelectOption> modelOptions,
        List<SessionConfigSelectOption> thinkingOptions,
        String selectedModeId,
        String selectedModelId,
        String selectedThinkingId,
        boolean updating // mirrors ConfigPanelController.isUpdatingConfigControls
) {
    public static ConfigPanelVM empty() {
        return new ConfigPanelVM(List.of(), List.of(), List.of(), null, null, null, false);
    }

    public ConfigPanelVM withModeOptions(List<SessionConfigSelectOption> v) {
        return new ConfigPanelVM(v, modelOptions, thinkingOptions,
                selectedModeId, selectedModelId, selectedThinkingId, updating);
    }
    public ConfigPanelVM withModelOptions(List<SessionConfigSelectOption> v) {
        return new ConfigPanelVM(modeOptions, v, thinkingOptions,
                selectedModeId, selectedModelId, selectedThinkingId, updating);
    }
    public ConfigPanelVM withThinkingOptions(List<SessionConfigSelectOption> v) {
        return new ConfigPanelVM(modeOptions, modelOptions, v,
                selectedModeId, selectedModelId, selectedThinkingId, updating);
    }
    public ConfigPanelVM withSelectedModeId(String v) {
        return new ConfigPanelVM(modeOptions, modelOptions, thinkingOptions,
                v, selectedModelId, selectedThinkingId, updating);
    }
    public ConfigPanelVM withSelectedModelId(String v) {
        return new ConfigPanelVM(modeOptions, modelOptions, thinkingOptions,
                selectedModeId, v, selectedThinkingId, updating);
    }
    public ConfigPanelVM withSelectedThinkingId(String v) {
        return new ConfigPanelVM(modeOptions, modelOptions, thinkingOptions,
                selectedModeId, selectedModelId, v, updating);
    }
    public ConfigPanelVM withUpdating(boolean v) {
        return new ConfigPanelVM(modeOptions, modelOptions, thinkingOptions,
                selectedModeId, selectedModelId, selectedThinkingId, v);
    }
}
