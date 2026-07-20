package github.anandb.netbeans.ui;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.ShortcutUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.border.TitledBorder;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.isBlank;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.SessionService;

// DSL-LEAF: keep imperative, wrap via UI.of(...) — NetBeans Options panel
// (GridBagLayout form). When the DSL lands, port the form to OptionsFormSpec;
// ACPOptionsPanelController (NetBeans SPI) stays as-is.
public class ACPOptionsPanel extends JPanel {
    private static final Logger LOG = Logger.from(ACPOptionsPanel.class);
    private static final long serialVersionUID = 1L;
    private final SessionService sessionService = PlatformBridge.sessionServiceSafe();
    private final ACPOptionsPanelController controller;
    private JLabel jLabel1;
    private JTextField pathField;
    private JLabel pathErrorLabel;
    private JTextArea preambleArea;
    private JScrollPane preambleScroll;
    private JButton browseButton;
    private JCheckBox echoCheckbox;
    private JCheckBox combineCheckbox;
    private JCheckBox checkForUpdatesCheckbox;
    private JCheckBox sortLinesCheckbox;
    private JCheckBox stashDiffCheckbox;
    private JCheckBox quickJumpCheckbox;
    private JSpinner idleTimeoutSpinner;
    private JSpinner maxMessagesSpinner;
    private JLabel argsLabel;
    private JTextField argsField;
    private JLabel iconLabel;
    private JTextField iconPathField;
    private JButton iconBrowseButton;
    private JLabel iconPreviewLabel;
    private transient IconPreviewManager iconPreviewManager;
    private JComboBox<String> toolbarIconCombo;
    private JComboBox<String> chatFontCombo;

    private String detectedPath;
    private boolean showingHint;
    private boolean userEditedPath;

    private static final Color HINT_COLOR = ThemeManager.getCurrentTheme().mutedForeground();

    ACPOptionsPanel(ACPOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        jLabel1 = new JLabel();
        pathField = new JTextField(40);
        browseButton = new JButton();
        argsLabel = new JLabel();
        argsField = new JTextField(40);
        echoCheckbox = new JCheckBox();
        combineCheckbox = new JCheckBox();
        checkForUpdatesCheckbox = new JCheckBox();
        sortLinesCheckbox = new JCheckBox();
        stashDiffCheckbox = new JCheckBox();
        quickJumpCheckbox = new JCheckBox();
        preambleArea = new JTextArea(5, 40);
        preambleScroll = new JScrollPane(preambleArea);
        iconLabel = new JLabel();
        iconPathField = new JTextField(40);
        iconBrowseButton = new JButton();
        iconPreviewLabel = new JLabel();

        iconPreviewManager = new IconPreviewManager(iconPreviewLabel, iconPathField, controller::changed);

        // --- Assistant Service ---
        JPanel servicePanel = createSectionPanel("LBL_ServiceHeader");
        servicePanel.setLayout(new GridBagLayout());
        int row = 0;

        jLabel1.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ExecutablePath"));
        servicePanel.add(jLabel1, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                       new Insets(0, 12, 5, 5)));

        pathField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) {
                userEditedPath = true;
                controller.changed();
            }
        });
        pathField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) { clearHint(); }

            @Override
            public void focusLost(FocusEvent e) { restoreHintIfEmpty(); }
        });
        servicePanel.add(pathField, UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 0, 5)));

        browseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        browseButton.addActionListener(evt -> browseButtonActionPerformed());
        servicePanel.add(browseButton, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                            new Insets(0, 0, 5, 0)));

        pathErrorLabel = new JLabel();
        pathErrorLabel.setForeground(UIManager.getColor("Panel.foreground"));
        pathErrorLabel.setFont(pathErrorLabel.getFont().deriveFont(Font.ITALIC, pathErrorLabel.getFont().getSize() - 1f));
        GridBagConstraints errGbc = UIUtils.createGbc(1, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 5, 5));
        errGbc.gridwidth = 2;
        errGbc.ipady = 0;
        servicePanel.add(pathErrorLabel, errGbc);

        argsLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ProcessArguments"));
        servicePanel.add(argsLabel, UIUtils.createGbc(0, ++row, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                         new Insets(0, 12, 5, 5)));

        argsField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) { controller.changed(); }
        });
        servicePanel.add(argsField, UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        add(servicePanel);
        add(javax.swing.Box.createVerticalStrut(8));

        // --- Updates ---
        JPanel updatesPanel = createSectionPanel("LBL_UpdatesHeader");
        updatesPanel.setLayout(new GridBagLayout());

        checkForUpdatesCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_CheckForUpdates"));
        checkForUpdatesCheckbox.addActionListener(evt -> controller.changed());
        GridBagConstraints gbcUpdates = UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 0));
        gbcUpdates.gridwidth = 3;
        updatesPanel.add(checkForUpdatesCheckbox, gbcUpdates);

        add(updatesPanel);
        add(javax.swing.Box.createVerticalStrut(8));

        // --- Chat Behavior ---
        JPanel behaviorPanel = createSectionPanel("LBL_BehaviorHeader");
        behaviorPanel.setLayout(new GridBagLayout());
        row = 0;

        echoCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EchoInput"));
        echoCheckbox.addActionListener(evt -> controller.changed());
        behaviorPanel.add(echoCheckbox, UIUtils.createGbc(0, row, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 0)));

        JLabel idleTimeoutLabel = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_SessionIdleTimeout"));
        behaviorPanel.add(idleTimeoutLabel, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 15, 5, 5)));
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(300, 0, 3600, 5);
        idleTimeoutSpinner = new JSpinner(spinnerModel);
        ((JSpinner.DefaultEditor) idleTimeoutSpinner.getEditor()).getTextField().setColumns(4);
        idleTimeoutSpinner.addChangeListener(evt -> controller.changed());
        behaviorPanel.add(idleTimeoutSpinner, UIUtils.createGbc(3, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 0, 5, 12)));

        combineCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_CombineToolThought"));
        combineCheckbox.addActionListener(evt -> controller.changed());
        behaviorPanel.add(combineCheckbox, UIUtils.createGbc(0, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 0)));

        JLabel maxMessagesLabel = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_MaxMessages"));
        behaviorPanel.add(maxMessagesLabel, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 15, 5, 5)));
        SpinnerNumberModel maxMessagesModel = new SpinnerNumberModel(25, 10, 200, 5);
        maxMessagesSpinner = new JSpinner(maxMessagesModel);
        ((JSpinner.DefaultEditor) maxMessagesSpinner.getEditor()).getTextField().setColumns(4);
        maxMessagesSpinner.addChangeListener(evt -> controller.changed());
        behaviorPanel.add(maxMessagesSpinner, UIUtils.createGbc(3, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 0, 5, 12)));

        add(behaviorPanel);
        add(javax.swing.Box.createVerticalStrut(8));

        // --- Actions ---
        JPanel actionsPanel = createSectionPanel("LBL_ActionsHeader");
        actionsPanel.setLayout(new GridBagLayout());

        sortLinesCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_SortLines"));
        sortLinesCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(sortLinesCheckbox, UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        stashDiffCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_StashDiff"));
        stashDiffCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(stashDiffCheckbox, UIUtils.createGbc(0, 1, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        String quickJumpShortcut = ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.GoToFileAction");
        quickJumpCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_QuickJump")
                + (quickJumpShortcut.isEmpty() ? "" : " (" + quickJumpShortcut + ")"));
        quickJumpCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(quickJumpCheckbox, UIUtils.createGbc(0, 2, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        add(actionsPanel);
        add(javax.swing.Box.createVerticalStrut(8));

        // --- Appearance (two-column) ---
        JPanel appearanceOuter = new JPanel(new GridBagLayout());
        appearanceOuter.setBorder(BorderFactory.createEmptyBorder());
        GridBagConstraints gbcAppOuter = new GridBagConstraints();
        gbcAppOuter.gridx = 0;
        gbcAppOuter.gridy = 0;
        gbcAppOuter.weightx = 1.0;
        gbcAppOuter.weighty = 0;
        gbcAppOuter.fill = GridBagConstraints.HORIZONTAL;
        gbcAppOuter.anchor = GridBagConstraints.NORTHWEST;

        JPanel appearanceLeft = createSectionPanel("LBL_AppearanceHeader");
        appearanceLeft.setLayout(new GridBagLayout());
        row = 0;

        iconLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_UserIcon"));
        appearanceLeft.add(iconLabel, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 5)));

        iconPathField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) {
                iconPreviewManager.updatePreview(iconPathField.getText());
                controller.changed();
            }
        });
        appearanceLeft.add(iconPathField, UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                             GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        iconBrowseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        iconBrowseButton.addActionListener(evt -> iconBrowseButtonActionPerformed());
        appearanceLeft.add(iconBrowseButton, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.WEST, new Insets(0, 0, 5, 0)));

        JLabel toolbarIconLabel = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ToolbarIconSize"));
        appearanceLeft.add(toolbarIconLabel, UIUtils.createGbc(0, ++row, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 5)));

        toolbarIconCombo = new JComboBox<>(new String[]{"16", "24", "28", "32", "36", "40", "48"});
        toolbarIconCombo.addActionListener(evt -> controller.changed());
        appearanceLeft.add(toolbarIconCombo, UIUtils.createGbc(1, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        row++;
        JLabel chatFontLabel = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ChatFontSize"));
        appearanceLeft.add(chatFontLabel, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 5)));

        chatFontCombo = new JComboBox<>(new String[]{"Inherited", "10", "11", "12", "13", "14", "16"});
        chatFontCombo.addActionListener(evt -> controller.changed());
        appearanceLeft.add(chatFontCombo, UIUtils.createGbc(1, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        JPanel previewPanel = new JPanel(new GridBagLayout());
        TitledBorder previewBorder = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "User Icon Preview",
                TitledBorder.LEADING,
                TitledBorder.TOP,
                previewPanel.getFont().deriveFont(Font.BOLD),
                ThemeManager.getCurrentTheme().foreground());
        previewPanel.setBorder(previewBorder);
        previewPanel.setPreferredSize(new Dimension(380, 160));
        previewPanel.add(iconPreviewLabel, UIUtils.createGbc(0, 0, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.WEST, new Insets(0, 12, 5, 12)));

        appearanceLeft.setPreferredSize(new Dimension(
                appearanceLeft.getPreferredSize().width,
                previewPanel.getPreferredSize().height));

        appearanceOuter.add(appearanceLeft, gbcAppOuter);
        appearanceOuter.add(previewPanel, UIUtils.createGbc(1, 0, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.NORTHWEST, new Insets(0, 8, 0, 0)));

        add(appearanceOuter);
        add(javax.swing.Box.createVerticalStrut(8));

        // --- Session Preamble ---
        JPanel preamblePanel = createSectionPanel("LBL_Preamble");
        preamblePanel.setLayout(new GridBagLayout());

        preambleArea.setLineWrap(true);
        preambleArea.setWrapStyleWord(true);
        preambleArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) { controller.changed(); }
        });
        JPopupMenu preambleMenu = new JPopupMenu();
        JMenuItem clearItem = new JMenuItem(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_PreambleClear"));
        clearItem.addActionListener(e -> preambleArea.setText(""));
        preambleMenu.add(clearItem);
        JMenuItem resetItem = new JMenuItem(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_PreambleReset"));
        resetItem.addActionListener(e -> preambleArea.setText(PluginSettings.getDefaultPreamble()));
        preambleMenu.add(resetItem);
        preambleArea.setComponentPopupMenu(preambleMenu);

        GridBagConstraints gbcPreambleScroll = UIUtils.createGbc(0, 0, 1.0, 1.0, GridBagConstraints.BOTH,
                GridBagConstraints.WEST, new Insets(0, 12, 15, 0));
        gbcPreambleScroll.gridwidth = 3;
        preamblePanel.add(preambleScroll, gbcPreambleScroll);

        add(preamblePanel);
    }

    private JPanel createSectionPanel(String headerKey) {
        JPanel panel = new JPanel();
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                NbBundle.getMessage(ACPOptionsPanel.class, headerKey),
                TitledBorder.LEADING,
                TitledBorder.TOP,
                panel.getFont().deriveFont(Font.BOLD),
                ThemeManager.getCurrentTheme().foreground());
        panel.setBorder(border);
        return panel;
    }

    private void browseButtonActionPerformed() {
        JFileChooser chooser = new JFileChooser();
        String currentPath = pathField.getText();
        if (!currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                chooser.setSelectedFile(currentFile);
            }
        }
        chooser.setDialogTitle(NbBundle.getMessage(ACPOptionsPanel.class, "TITLE_SelectExecutable"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            clearHint();
            userEditedPath = true;
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            controller.changed();
        }
    }

    private void iconBrowseButtonActionPerformed() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images (*.png)", "png"));
        chooser.setDialogTitle(NbBundle.getMessage(ACPOptionsPanel.class, "TITLE_SelectIcon"));
        String currentPath = iconPathField.getText();
        if (!currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                chooser.setSelectedFile(currentFile);
            }
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            iconPathField.setText(path);
            iconPreviewManager.updatePreview(path);
            controller.changed();
        }
    }

    private String previousIconPath;

    void load() {
        userEditedPath = false;
        String savedPath = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get(PreferenceKeys.ACP_EXECUTABLE_PATH, null);
        detectedPath = BinaryResolver.findOnPath();

        if (savedPath != null && !savedPath.isEmpty()) {
            pathField.setText(savedPath);
            pathField.setForeground(null);
            showingHint = false;
        } else if (detectedPath != null) {
            pathField.setText(detectedPath);
            pathField.setForeground(HINT_COLOR);
            showingHint = true;
        } else {
            pathField.setText(NbBundle.getMessage(ACPOptionsPanel.class, "HINT_NotFoundOnPath"));
            pathField.setForeground(HINT_COLOR);
            showingHint = true;
        }

        argsField.setText(NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get(PreferenceKeys.PROCESS_ARGUMENTS, "acp"));

        preambleArea.setText(PluginSettings.getPreamble());
        echoCheckbox.setSelected(NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("echoUserInput", true));
        combineCheckbox.setSelected(NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("combineToolThought", true));
        checkForUpdatesCheckbox.setSelected(NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).getBoolean(PreferenceKeys.CHECK_FOR_UPDATES, true));
        idleTimeoutSpinner.setValue(PluginSettings.getSessionIdleTimeout());
        maxMessagesSpinner.setValue(PluginSettings.getMaxMessages());
        previousIconPath = PluginSettings.getCustomUserIcon();
        iconPathField.setText(previousIconPath);
        iconPreviewManager.updatePreview(iconPathField.getText());

        int currentSize = PluginSettings.getToolbarIconSize();
        toolbarIconCombo.setSelectedItem(String.valueOf(currentSize));

        int currentChatFont = PluginSettings.getChatFontSize();
        chatFontCombo.setSelectedItem(currentChatFont < 0 ? "Inherited" : String.valueOf(currentChatFont));

        sortLinesCheckbox.setSelected(NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).getBoolean(PreferenceKeys.ACTIONS_SORT_LINES, true));
        stashDiffCheckbox.setSelected(NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).getBoolean(PreferenceKeys.ACTIONS_STASH_DIFF, true));
        quickJumpCheckbox.setSelected(NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).getBoolean(PreferenceKeys.ACTIONS_QUICK_JUMP, true));
    }

    private void clearHint() {
        if (showingHint) {
            pathField.setText("");
            pathField.setForeground(null);
            showingHint = false;
        }
    }

    private void restoreHintIfEmpty() {
        if (pathField.getText().isEmpty()) {
            if (detectedPath != null) {
                pathField.setText(detectedPath);
            } else {
                pathField.setText(NbBundle.getMessage(ACPOptionsPanel.class, "HINT_NotFoundOnPath"));
            }
            pathField.setForeground(HINT_COLOR);
            showingHint = true;
        }
    }

    void store() {
        String pathToSave = showingHint ? "" : pathField.getText();
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(PreferenceKeys.ACP_EXECUTABLE_PATH, pathToSave);
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(PreferenceKeys.PROCESS_ARGUMENTS, argsField.getText());
        PluginSettings.setPreamble(preambleArea.getText());
        boolean changedCombine = combineCheckbox.isSelected()
                != NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("combineToolThought", true);
        NbPreferences.forModule(ACPOptionsPanel.class).putBoolean("echoUserInput", echoCheckbox.isSelected());
        NbPreferences.forModule(ACPOptionsPanel.class).putBoolean("combineToolThought", combineCheckbox.isSelected());
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.CHECK_FOR_UPDATES, checkForUpdatesCheckbox.isSelected());
        PluginSettings.setSessionIdleTimeout((Integer) idleTimeoutSpinner.getValue());
        PluginSettings.setMaxMessages((Integer) maxMessagesSpinner.getValue());

        if (changedCombine) {
            LOG.info("Combine tool/thought toggled to {0}", combineCheckbox.isSelected());
            String currentId = sessionService.get().getCurrentSessionId();
            if (currentId != null) {
                SwingUtilities.invokeLater(() -> sessionService.get().loadSession(currentId));
            }
        }
        PluginSettings.setCustomUserIcon(iconPathField.getText());

        String selectedSize = (String) toolbarIconCombo.getSelectedItem();
        if (selectedSize != null) {
            PluginSettings.setToolbarIconSize(Integer.parseInt(selectedSize));
        }

        String selectedChatFont = (String) chatFontCombo.getSelectedItem();
        if (selectedChatFont != null) {
            PluginSettings.setChatFontSize("Inherited".equals(selectedChatFont) ? -1 : Integer.parseInt(selectedChatFont));
        }

        PluginSettings.setSortLinesEnabled(sortLinesCheckbox.isSelected());
        PluginSettings.setStashDiffEnabled(stashDiffCheckbox.isSelected());
        PluginSettings.setQuickJumpEnabled(quickJumpCheckbox.isSelected());

        String newIconPath = iconPathField.getText();
        String oldPath = previousIconPath != null ? previousIconPath : "";
        String newPath = newIconPath != null ? newIconPath : "";
        if (!oldPath.equals(newPath)) {
            LOG.info("User icon changed: {0} -> {1}", oldPath, newPath);
            previousIconPath = newPath;
            SwingUtilities.invokeLater(() -> {
                SessionControl sm = sessionService.get();
                String currentId = sm.getCurrentSessionId();
                if (currentId != null) {
                    sm.loadSession(currentId);
                }
            });
        }
    }

    boolean valid() {
        if (!userEditedPath) {
            pathErrorLabel.setText("");
            return true;
        }
        String path = pathField.getText();
        if (isBlank(path)) {
            pathErrorLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "ERR_EmptyPath"));
            pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            return false;
        }
        File f = new File(path.trim());
        if (!f.exists()) {
            pathErrorLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "ERR_PathNotFound"));
            pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            return false;
        }
        if (!f.isFile()) {
            pathErrorLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "ERR_PathNotFile"));
            pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            return false;
        }
        if (!f.canExecute()) {
            pathErrorLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "ERR_PathNotExecutable"));
            pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            return false;
        }
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows && !path.toLowerCase().endsWith(".exe")) {
            pathErrorLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "ERR_MissingExeExtension"));
            pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            return false;
        }
        pathErrorLabel.setText("");
        return true;
    }

}
