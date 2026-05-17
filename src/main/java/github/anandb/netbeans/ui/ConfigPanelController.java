package github.anandb.netbeans.ui;

import static org.apache.commons.lang3.StringUtils.split;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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

import github.anandb.netbeans.manager.SessionManager;
import github.anandb.netbeans.model.ConfigItem;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionConfigSelectOption;
import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;

@NbBundle.Messages({
    "LBL_Agent=Agent:",
    "LBL_Model=Model:",
    "LBL_Thinking=Thinking:",
    "HINT_CopyModelID=Copy Model ID"
})
public class ConfigPanelController {

    private static final Logger LOG = new Logger(ConfigPanelController.class);

    private final JPanel configPanel;
    private final JComboBox<ConfigItem> modeCombo;
    private final JComboBox<ConfigItem> modelCombo;
    private final JComboBox<ConfigItem> thinkingCombo;
    private volatile Runnable onModelSelectedCallback;
    private volatile Runnable onModeSelectedCallback;
    private volatile Runnable onThinkingSelectedCallback;
    private boolean isUpdatingConfigControls = false;
    private static String lastSelectedModelId;

    private final LinkedHashMap<String, List<ConfigItem>> modelVariants = new LinkedHashMap<>();
    private String currentConfigModelId = null;

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

        modeCombo = new JComboBox<>();
        modelCombo = new JComboBox<>();
        thinkingCombo = new JComboBox<>();

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

    public void popupModelCombo() {
        SwingUtilities.invokeLater(() -> {
            modelCombo.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> modelCombo.setPopupVisible(true));
        });
    }

    public void popupModeCombo() {
        SwingUtilities.invokeLater(() -> {
            modeCombo.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> modeCombo.setPopupVisible(true));
        });
    }

    public void popupThinkingCombo() {
        SwingUtilities.invokeLater(() -> {
            thinkingCombo.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> thinkingCombo.setPopupVisible(true));
        });
    }

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
                    SessionManager.getInstance().setSessionConfigOption(sessionId, opt.id(), selectedValue);
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
        if (lastSelectedModelId == null && envModel != null && !envModel.isEmpty() && modelCombo.getItemCount() == 0) {
            modelCombo.addItem(new ConfigItem(envModel, envModel));
            lastSelectedModelId = envModel;
        }
    }

    public void updateConfigControls(List<SessionConfigOption> options, boolean forceStartupDefaults) {
        SwingUtilities.invokeLater(() -> {
            isUpdatingConfigControls = true;
            try {
                for (SessionConfigOption opt : options) {
                    JComboBox<ConfigItem> combo = resolveComboTarget(opt.category());
                    if (combo == null) continue;

                    if ("model".equals(opt.category())) {
                        parseModelVariants(opt);
                    }

                    combo.removeAllItems();

                    String valueToSelect = resolveStartupValue(opt, isThinkingCategory(opt.category()), opt.currentValue(), forceStartupDefaults);
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

    private void parseModelVariants(SessionConfigOption opt) {
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

    private String resolveStartupValue(SessionConfigOption opt, boolean isThinking, String currentValue, boolean force) {
        if (!force) return currentValue;
        String currentId = SessionManager.getInstance().getCurrentSessionId();

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
                    SessionManager.getInstance().setSessionConfigOption(currentId, opt.id(), match);
                    return match;
                }
            }
            if (lastSelectedModelId != null && !lastSelectedModelId.equalsIgnoreCase(currentValue)) {
                SessionManager.getInstance().setSessionConfigOption(currentId, opt.id(), lastSelectedModelId);
                return lastSelectedModelId;
            }
        }

        return currentValue;
    }

    private String sendAndReturn(SessionConfigOption opt, String forcedValue, String currentId) {
        if (!forcedValue.equalsIgnoreCase(opt.currentValue()) && currentId != null) {
            LOG.fine("Forcing default: {0}={1} (was {2})", new Object[]{opt.id(), forcedValue, opt.currentValue()});
            SessionManager.getInstance().setSessionConfigOption(currentId, opt.id(), forcedValue);
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

    private ConfigItem populateComboBox(JComboBox<ConfigItem> combo, String category, List<SessionConfigSelectOption> options, String valueToSelect) {
        ConfigItem selected = null;
        if ("model".equals(category)) {
            for (Map.Entry<String, List<ConfigItem>> entry : modelVariants.entrySet()) {
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
            for (SessionConfigSelectOption o : options) {
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
        tabNameUpdater.accept(selected != null ? selected.name() : null);
        ConfigItem selItem = (ConfigItem) combo.getSelectedItem();
        if (selItem != null) {
            updateThinkingComboForModel(selItem.value());
        }
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
            if (isUpdatingConfigControls) {
                return;
            }
            Object selected = combo.getSelectedItem();
            ConfigItem item = resolveComboSelection(combo, selected);
            if (item == null) return;

            // UI side effects only (no API calls while popup is visible)
            if (combo == modelCombo) {
                lastSelectedModelId = item.value();
                updateThinkingComboForModel(item.value());
                tabNameUpdater.accept(item.name());
            }
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
                if (selected != null && SessionManager.getInstance().getCurrentSessionId() != null) {
                    String currentId = SessionManager.getInstance().getCurrentSessionId();
                    String prevModelId = combo == modelCombo ? lastSelectedModelId : null;
                    LOG.fine("Config update: {0}={1} for session {2}", new Object[]{configId, selected.value(), currentId});
                    SessionManager.getInstance().setSessionConfigOption(currentId, configId, selected.value())
                        .exceptionally(ex -> {
                            LOG.warn("Failed to set config {0}: {1}", configId, ex.getMessage());
                            if (combo == modelCombo && prePopupSelection[0] != null && prevModelId != null) {
                                SwingUtilities.invokeLater(() -> {
                                    isUpdatingConfigControls = true;
                                    try {
                                        lastSelectedModelId = prevModelId;
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

    private void updateThinkingComboForModel(String baseId) {
        boolean alreadyUpdating = isUpdatingConfigControls;
        isUpdatingConfigControls = true;
        try {
            thinkingCombo.removeAllItems();
            List<ConfigItem> variants = modelVariants.get(baseId);
            if (variants != null && !variants.isEmpty()) {
                ConfigItem selectedVariant = null;
                for (ConfigItem v : variants) {
                    thinkingCombo.addItem(v);
                    if (v.value().equalsIgnoreCase(currentConfigModelId)) {
                        selectedVariant = v;
                    }
                }
                if (selectedVariant != null) {
                    thinkingCombo.setSelectedItem(selectedVariant);
                } else {
                    thinkingCombo.setSelectedIndex(0);
                }
                thinkingCombo.setEnabled(true);
            } else {
                thinkingCombo.setEnabled(false);
            }
        } finally {
            isUpdatingConfigControls = alreadyUpdating;
        }
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
