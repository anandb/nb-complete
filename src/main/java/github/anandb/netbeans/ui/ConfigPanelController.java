package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import github.anandb.netbeans.model.ModelRecords.ConfigItem;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionConfigSelectOption;
import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;

import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.SessionService;

// DSL-LEAF: not a view-pure form — builds the agent/model/thinking combo panel
// (GridBagLayout). Migration target: OptionsFormSpec + ConfigComboSpec; the
// selection callbacks + ModelVariantResolver wiring stay imperative.
public class ConfigPanelController {

    private static final Logger LOG = Logger.from(ConfigPanelController.class);

    private final SessionService sessionService = PlatformBridge.sessionServiceSafe();

    private final JPanel configPanel;
    private final JComboBox<ConfigItem> modeCombo;
    private final JComboBox<ConfigItem> modelCombo;
    private final JComboBox<ConfigItem> thinkingCombo;
    private volatile Runnable onModelSelectedCallback;
    private volatile Runnable onModeSelectedCallback;
    private volatile Runnable onThinkingSelectedCallback;
    private boolean isUpdatingConfigControls = false;

    private final ModelVariantResolver modelResolver = new ModelVariantResolver();

    private final Consumer<String> tabNameUpdater;

    public ConfigPanelController(Consumer<String> tabNameUpdater) {
        this.tabNameUpdater = tabNameUpdater;

        configPanel = new JPanel(new GridBagLayout());
        configPanel.setVisible(false);
        configPanel.setOpaque(false);
        configPanel.setBorder(new EmptyBorder(5, 12, 5, 12));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 2, 8);

        modeCombo = new UIUtils.WrappingComboBox<>();
        modelCombo = new UIUtils.WrappingComboBox<>();
        thinkingCombo = new UIUtils.WrappingComboBox<>();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        configPanel.add(new JLabel(NbBundle.getMessage(ConfigPanelController.class, "LBL_Agent")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.2;
        configPanel.add(modeCombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        configPanel.add(new JLabel(NbBundle.getMessage(ConfigPanelController.class, "LBL_Model")), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.7;
        configPanel.add(modelCombo, gbc);

        gbc.gridx = 4;
        gbc.weightx = 0;
        gbc.insets = new Insets(2, 0, 2, 4);
        String copyHint = NbBundle.getMessage(ConfigPanelController.class, "HINT_CopyModelID");
        JButton copyModelBtn = UIUtils.createToolbarButton("copy.svg", 20, copyHint, e -> {
            ConfigItem selected = (ConfigItem) modelCombo.getSelectedItem();
            if (selected != null && selected.value() != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(selected.value()), null);
            }
        });
        configPanel.add(copyModelBtn, gbc);
        gbc.insets = new Insets(2, 0, 2, 8);

        gbc.gridx = 5;
        gbc.weightx = 0;
        configPanel.add(new JLabel(NbBundle.getMessage(ConfigPanelController.class, "LBL_Thinking")), gbc);

        gbc.gridx = 6;
        gbc.weightx = 0.1;
        configPanel.add(thinkingCombo, gbc);
    }

    public JPanel getComponent() {
        return configPanel;
    }

    public void addKeyListenerToInputs(KeyListener listener) {
        modeCombo.addKeyListener(listener);
        modelCombo.addKeyListener(listener);
        thinkingCombo.addKeyListener(listener);
    }

    public void removeKeyListenerFromInputs(KeyListener listener) {
        modeCombo.removeKeyListener(listener);
        modelCombo.removeKeyListener(listener);
        thinkingCombo.removeKeyListener(listener);
    }

    public void popupCombo(JComboBox<ConfigItem> combo) {
        SwingUtilities.invokeLater(() -> {
            combo.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> combo.setPopupVisible(true));
        });
    }

    JComboBox<ConfigItem> getModelCombo() { return modelCombo; }

    JComboBox<ConfigItem> getModeCombo() { return modeCombo; }

    JComboBox<ConfigItem> getThinkingCombo() { return thinkingCombo; }

    public void updateConfigControls(List<SessionConfigOption> options) {
        updateConfigControls(options, false);
    }

    public void applyPreSelectedConfigValues(String sessionId, List<SessionConfigOption> configOptions) {
        for (SessionConfigOption opt : configOptions) {
            JComboBox<ConfigItem> combo = null;
            if ("mode".equals(opt.category())) {
                combo = modeCombo;
            } else if ("model".equals(opt.category())) {
                combo = modelCombo;
            } else if (opt.category() != null
                    && (opt.category().contains("thinking") || opt.category().contains("thought"))) {
                combo = thinkingCombo;
            }

            if (combo != null && combo.getSelectedItem() instanceof ConfigItem selectedItem) {
                String selectedValue = selectedItem.value();
                String currentValue = opt.currentValue();
                if (selectedValue != null && !selectedValue.isEmpty() && !selectedValue.equals(currentValue)) {
                    LOG.fine("Applying pre-selected config: {0}={1} (server default was {2})",
                            new Object[]{opt.id(), selectedValue, currentValue});
                    sessionService.get().setSessionConfigOption(sessionId, opt.id(), selectedValue);
                }
            }
        }
    }

    public void ensureDefaultModelSelected() {
        isUpdatingConfigControls = true;
        try {
            if (modelCombo.getItemCount() > 0 && modelCombo.getSelectedIndex() < 0) {
                modelCombo.setSelectedIndex(0);
            }
        } finally {
            isUpdatingConfigControls = false;
        }
    }

    public void ensureDefaultModelAdded() {
        String envModel = System.getenv("OPENCODE_MODEL");
        if (modelResolver.getLastSelectedModelId() == null && envModel != null && !envModel.isEmpty() && modelCombo.getItemCount() == 0) {
            modelCombo.addItem(new ConfigItem(envModel, envModel));
            modelResolver.setLastSelectedModelId(envModel);
        }
    }

    public void updateConfigControls(List<SessionConfigOption> options, boolean forceStartupDefaults) {
        SwingUtilities.invokeLater(() -> {
            isUpdatingConfigControls = true;
            try {
                // First pass: parse model variants before any combo population,
                // so thinking-level filtering can rely on modelVariants being ready.
                for (SessionConfigOption opt : options) {
                    if ("model".equals(opt.category())) {
                        modelResolver.parseModelVariants(opt);
                    }
                }

                // Second pass: populate all combos with variants already resolved.
                for (SessionConfigOption opt : options) {
                    JComboBox<ConfigItem> combo = resolveComboTarget(opt.category());
                    if (combo == null) continue;

                    combo.removeAllItems();

                    String valueToSelect = modelResolver.resolveStartupValue(
                            opt, isThinkingCategory(opt.category()),
                            opt.currentValue(), forceStartupDefaults);
                    ConfigItem selected = populateComboBox(combo, opt.category(), opt.options(), valueToSelect);

                    if (combo.getActionListeners().length == 0) {
                        setupConfigCombo(combo, opt.id());
                    }

                    if (selected != null) {
                        combo.setSelectedItem(selected);
                    } else if (combo.getItemCount() > 0) {
                        combo.setSelectedIndex(0);
                    }

                    if ("model".equals(opt.category())) {
                        postProcessModel(combo, selected);
                    }
                }
                thinkingCombo.setEnabled(thinkingCombo.getItemCount() > 0);
                SwingUtilities.invokeLater(() -> tabNameUpdater.accept(buildTabLabel()));
                if (thinkingCombo.getActionListeners().length == 0) {
                    setupConfigCombo(thinkingCombo, "thinking");
                }
            } finally {
                isUpdatingConfigControls = false;
            }
        });
    }

    private JComboBox<ConfigItem> resolveComboTarget(String category) {
        if ("mode".equals(category)) return modeCombo;
        if ("model".equals(category)) return modelCombo;
        if (category != null && (category.contains("thinking") || category.contains("thought"))) return thinkingCombo;
        return null;
    }

    private static boolean isThinkingCategory(String category) {
        return category != null && (category.contains("thinking") || category.contains("thought"));
    }

    private ConfigItem populateComboBox(JComboBox<ConfigItem> combo, String category, List<SessionConfigSelectOption> options, String valueToSelect) {
        ConfigItem selected = null;
        if ("model".equals(category)) {
            for (Map.Entry<String, List<ConfigItem>> entry : modelResolver.getModelVariants().entrySet()) {
                List<ConfigItem> variants = entry.getValue();
                ConfigItem baseItem = variants.get(0);
                ConfigItem item = new ConfigItem(baseItem.baseName(), entry.getKey());
                combo.addItem(item);
                for (ConfigItem v : variants) {
                    if (v.value().equalsIgnoreCase(valueToSelect)) {
                        selected = item;
                        break;
                    }
                }
            }
        } else {
            boolean hasModelVariants = !modelResolver.getModelVariants().isEmpty();
            boolean isThinking = category != null && (category.contains("thinking") || category.contains("thought"));
            for (SessionConfigSelectOption o : options) {
                // When model has variants that encode thinking level, skip
                // "default"/"None"/empty options — GPT models need a real level.
                if (isThinking && hasModelVariants && isDefaultOrEmptyOption(o)) continue;
                ConfigItem item = new ConfigItem(o.name(), o.value());
                combo.addItem(item);
                if (o.value() != null && valueToSelect != null && o.value().equalsIgnoreCase(valueToSelect)) {
                    selected = item;
                }
            }
        }
        return selected;
    }

    private void postProcessModel(JComboBox<ConfigItem> combo, ConfigItem selected) {
        combo.setEditable(false);
        tabNameUpdater.accept(buildTabLabel());
    }

    private void setupConfigCombo(JComboBox<ConfigItem> combo, String configId) {
        Font btnFont = UIManager.getFont("Button.font");
        if (btnFont != null) {
            combo.setFont(btnFont);
        }

        final Object[] prePopupSelection = {null};

        combo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && combo.isPopupVisible()) {
                    e.consume();
                    if (prePopupSelection[0] != null) {
                        combo.setSelectedItem(prePopupSelection[0]);
                    }
                    combo.setPopupVisible(false);
                }
            }
        });

        combo.addActionListener(e -> {
            if (isUpdatingConfigControls) return;
            ConfigItem item = (ConfigItem) combo.getSelectedItem();
            if (item != null) {
                if (combo == modelCombo) {
                    modelResolver.setLastSelectedModelId(item.value());
                }
            }
            tabNameUpdater.accept(buildTabLabel());
        });
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                prePopupSelection[0] = combo.getSelectedItem();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                if (isUpdatingConfigControls) return;

                ConfigItem selected = resolveComboSelection(combo, combo.getSelectedItem());
                if (selected != null && sessionService.get().getCurrentSessionId() != null) {
                    String currentId = sessionService.get().getCurrentSessionId();
                    String prevModelId = combo == modelCombo ? modelResolver.getLastSelectedModelId() : null;
                    LOG.fine("Config update: {0}={1} for session {2}", new Object[]{configId, selected.value(), currentId});
                    sessionService.get().setSessionConfigOption(currentId, configId, selected.value())
                        .exceptionally(ex -> {
                            LOG.warn("Failed to set config {0}: {1}", configId, ExceptionUtils.getMessage(ex));
                            if (combo == modelCombo && prePopupSelection[0] != null && prevModelId != null) {
                                SwingUtilities.invokeLater(() -> {
                                    isUpdatingConfigControls = true;
                                    try {
                                        modelResolver.setLastSelectedModelId(prevModelId);
                                        combo.setSelectedItem(prePopupSelection[0]);
                                    } finally {
                                        isUpdatingConfigControls = false;
                                    }
                                });
                            }
                            return null;
                        });
                }

                if (combo == modelCombo && onModelSelectedCallback != null) {
                    onModelSelectedCallback.run();
                } else if (combo == modeCombo && onModeSelectedCallback != null) {
                    onModeSelectedCallback.run();
                } else if (combo == thinkingCombo && onThinkingSelectedCallback != null) {
                    onThinkingSelectedCallback.run();
                }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                if (prePopupSelection[0] != null) {
                    combo.setSelectedItem(prePopupSelection[0]);
                }
            }
        });
    }

    private static ConfigItem resolveComboSelection(JComboBox<ConfigItem> combo, Object selected) {
        if (selected instanceof ConfigItem item) {
            return item;
        } else if (selected instanceof String val) {
            for (int i = 0; i < combo.getItemCount(); i++) {
                ConfigItem current = combo.getItemAt(i);
                if (current.value().equalsIgnoreCase(val) || current.name().equalsIgnoreCase(val)) {
                    combo.setSelectedItem(current);
                    return current;
                }
            }
        }
        return null;
    }

    /**
     * Builds the tab label from current combo selections.
     * Format: "model/level | agent" or just "model" if agent/level unavailable.
     * The agent name is rendered in bold blue using HTML.
     */
    private String buildTabLabel() {
        String model = null;
        String agent = null;
        String level = null;

        if (modelCombo.getSelectedItem() instanceof ConfigItem m) {
            model = m.name();
        }
        if (modeCombo.getSelectedItem() instanceof ConfigItem a) {
            agent = a.name();
        }
        if (thinkingCombo.getSelectedItem() instanceof ConfigItem t) {
            level = t.name();
        }

        if (model == null) return null;
        String agentDisplay = agent != null ? capitalize(agent) : null;
        if (agentDisplay != null && level != null) {
            return "<html>" + model + "/" + level + " | <font color='#3A7FBF'><b>" + agentDisplay + "</b></font>";
        } else if (agentDisplay != null) {
            return "<html>" + model + " | <font color='#3A7FBF'><b>" + agentDisplay + "</b></font>";
        }
        return model;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    /** Returns true if the option value is meaningless for models that need a real thinking level. */
    private static boolean isDefaultOrEmptyOption(SessionConfigSelectOption o) {
        if (o.value() == null || o.value().isBlank()) return true;
        String v = o.value().toLowerCase(java.util.Locale.ROOT);
        return "default".equals(v) || "none".equals(v);
    }



    public void setOnModelSelectedCallback(Runnable r) {
        this.onModelSelectedCallback = r;
    }

    public void setOnModeSelectedCallback(Runnable r) {
        this.onModeSelectedCallback = r;
    }

    public void setOnThinkingSelectedCallback(Runnable r) {
        this.onThinkingSelectedCallback = r;
    }
}
