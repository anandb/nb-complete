package github.anandb.netbeans.ui;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.ShortcutUtils;
import github.anandb.netbeans.support.XmlUtils;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.SimpleValueNames;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Box;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.isBlank;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import org.openide.awt.NotificationDisplayer;
import github.anandb.netbeans.ui.platform.SessionService;

// DSL-LEAF: keep imperative, wrap via UI.of(...) — NetBeans Options panel
// (GridBagLayout form). When the DSL lands, port the form to OptionsFormSpec;
// ACPOptionsPanelController (NetBeans SPI) stays as-is.
public class ACPOptionsPanel extends JPanel implements OptionsPanel {
    private static final Logger LOG = Logger.from(ACPOptionsPanel.class);
    private static final long serialVersionUID = 1L;
    /** Target tooltip line length in characters; the pixel width is derived from the LAF font. */
    private static final int TOOLTIP_CHARS = 85;
    /** Never wrap narrower than this, even if the LAF reports a tiny tooltip font. */
    private static final int MIN_TOOLTIP_WIDTH = 420;
    /** Tokens longer than this get zero-width breaks after path/word separators. */
    private static final int LONG_TOKEN_CHARS = 40;
    /** Swing's HTML view has no word-break, so long paths are broken after these characters. */
    private static final Pattern LONG_TOKEN = Pattern.compile("[^\\s<>]{" + LONG_TOKEN_CHARS + ",}");
    private static final char ZERO_WIDTH_SPACE = '\u200B';
    private static volatile Font cachedTooltipFont;
    private static volatile int cachedTooltipWidth;
    private final SessionService sessionService = PlatformBridge.sessionServiceSafe();
    private final ACPOptionsPanelController controller;
    private JLabel jLabel1;
    private JComboBox<String> pathCombo;
    private JLabel pathErrorLabel;
    private JButton browseButton;
    private JCheckBox echoCheckbox;
    private JCheckBox combineCheckbox;
    private JCheckBox checkForUpdatesCheckbox;
    private JCheckBox sortLinesCheckbox;
    private JCheckBox compactJsonCheckbox;
    private JCheckBox searchWebCheckbox;
    private JCheckBox showAnnotationsCheckbox;
    private JCheckBox viewFileHistoryCheckbox;
    private JCheckBox stashDiffCheckbox;
    private JCheckBox quickJumpCheckbox;
    private JCheckBox copyAllItemsCheckbox;
    private JCheckBox autoBackupChangesCheckbox;
    private JCheckBox miniAssistantCheckbox;
    private JCheckBox taskRepositoryCheckbox;
    private JCheckBox markdownProjectCheckbox;
    private JCheckBox mcpServerCheckbox;
    private JSpinner mcpPortSpinner;
    private JCheckBox useWslCheckbox;
    private JCheckBox projectSwitchRestartCheckbox;
    private JSpinner idleTimeoutSpinner;
    private JSpinner maxMessagesSpinner;
    private JLabel argsLabel;
    private JTextField argsField;
    private JLabel argsHintLabel;
    private JLabel iconLabel;
    private JTextField iconPathField;
    private JButton iconBrowseButton;
    private JLabel iconPreviewLabel;
    private JPanel pendingRestartBar;
    private JLabel pendingRestartTitle;
    private JLabel pendingRestartBody;
    private transient IconPreviewManager iconPreviewManager;
    private JComboBox<String> toolbarIconCombo;
    private JComboBox<String> chatFontCombo;
    private JButton editPreambleButton;
    private JCheckBox preambleEnabledCheckbox;
    private JSpinner lineHeightCorrectionSpinner;

    private boolean userEditedPath;
    private String preambleText;
    private String previousIconPath;
    /** Maps raw path → display label (e.g. "/usr/local/bin/opencode" → "/usr/local/bin/opencode (WSL)"). */
    private final Map<String, String> pathLabels = new LinkedHashMap<>();

    ACPOptionsPanel(ACPOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        createPendingRestartBar();

        jLabel1 = new JLabel();
        pathCombo = new JComboBox<>();
        pathCombo.setEditable(true);
        pathCombo.setRenderer(new PathComboRenderer());
        browseButton = new JButton();
        argsLabel = new JLabel();
        argsField = new JTextField(40);
        echoCheckbox = new JCheckBox();
        combineCheckbox = new JCheckBox();
        checkForUpdatesCheckbox = new JCheckBox();
        sortLinesCheckbox = new JCheckBox();
        compactJsonCheckbox = new JCheckBox();
        searchWebCheckbox = new JCheckBox();
        showAnnotationsCheckbox = new JCheckBox();
        viewFileHistoryCheckbox = new JCheckBox();
        stashDiffCheckbox = new JCheckBox();
        quickJumpCheckbox = new JCheckBox();
        copyAllItemsCheckbox = new JCheckBox();
        autoBackupChangesCheckbox = new JCheckBox();
        miniAssistantCheckbox = new JCheckBox();
        taskRepositoryCheckbox = new JCheckBox();
        markdownProjectCheckbox = new JCheckBox();
        mcpServerCheckbox = new JCheckBox();
        preambleEnabledCheckbox = new JCheckBox();
        projectSwitchRestartCheckbox = new JCheckBox();
        iconLabel = new JLabel();
        iconPathField = new JTextField(40);
        iconBrowseButton = new JButton();
        iconPreviewLabel = new JLabel();

        iconPreviewManager = new IconPreviewManager(iconPreviewLabel, iconPathField, controller::changed);

        // --- Assistant Service ---
        JPanel servicePanel = createSectionPanel("LBL_ServiceHeader");
        servicePanel.setLayout(new GridBagLayout());
        int row = 0;

        jLabel1.setText(scopedLabel("LBL_ExecutablePath", PluginSettings.Effect.IDE_RESTART));
        jLabel1.setToolTipText(tooltip("TT_ExecutablePath"));
        servicePanel.add(jLabel1, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                       new Insets(0, 12, 5, 5)));

        pathCombo.setToolTipText(tooltip("TT_ExecutablePath"));
        pathCombo.addActionListener(evt -> {
            if (pathCombo.isPopupVisible()) {
                userEditedPath = true;
                // Auto-fill launch args from catalog when user picks a known harness
                String path = getExecutablePath();
                String binName = BinaryResolver.binaryNameFromPath(path);
                HarnessCatalog.Harness h = binName != null ? HarnessCatalog.byBinaryName(binName) : null;
                if (h != null) {
                    argsField.setText(h.launchArgs());
                }
                updateGeminiModelHint();
                controller.changed();
            }
        });
        pathCombo.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) {
                userEditedPath = true;
                controller.changed();
            }
        });
        servicePanel.add(pathCombo, UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 0, 5)));

        browseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        browseButton.setToolTipText(tooltip("TT_BrowseExecutable"));
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

        argsLabel.setText(scopedLabel("LBL_ProcessArguments", PluginSettings.Effect.IDE_RESTART));
        argsLabel.setToolTipText(tooltip("TT_ProcessArguments"));
        servicePanel.add(argsLabel, UIUtils.createGbc(0, ++row, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                         new Insets(0, 12, 5, 5)));

        argsField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) { controller.changed(); }
        });
        argsField.setToolTipText(tooltip("TT_ProcessArguments"));
        servicePanel.add(argsField, UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 2, 5)));

        argsHintLabel = new JLabel();
        argsHintLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        argsHintLabel.setFont(argsHintLabel.getFont().deriveFont(Font.ITALIC, argsHintLabel.getFont().getSize() - 1f));
        GridBagConstraints hintGbc = UIUtils.createGbc(1, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 0, 5, 5));
        hintGbc.gridwidth = 2;
        servicePanel.add(argsHintLabel, hintGbc);

        useWslCheckbox = new JCheckBox();
        useWslCheckbox.setText(scopedLabel("LBL_UseWsl", PluginSettings.Effect.IDE_RESTART));
        useWslCheckbox.setToolTipText(tooltip("TT_UseWsl"));
        useWslCheckbox.addActionListener(evt -> controller.changed());
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            GridBagConstraints gbcWsl = UIUtils.createGbc(0, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                    GridBagConstraints.WEST, new Insets(0, 12, 5, 0));
            gbcWsl.gridwidth = 3;
            servicePanel.add(useWslCheckbox, gbcWsl);
        }

        add(servicePanel);
        add(Box.createVerticalStrut(4));

        // --- System ---
        JPanel systemPanel = createSectionPanel("LBL_SystemHeader");
        systemPanel.setLayout(new GridBagLayout());

        checkForUpdatesCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_CheckForUpdates"));
        checkForUpdatesCheckbox.setToolTipText(tooltip("TT_CheckForUpdates"));
        checkForUpdatesCheckbox.addActionListener(evt -> controller.changed());
        systemPanel.add(checkForUpdatesCheckbox, UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        mcpServerCheckbox.setText(scopedLabel("LBL_EnableMcpServers", PluginSettings.Effect.IDE_RESTART));
        mcpServerCheckbox.setToolTipText(tooltip("TT_EnableMcpServers"));
        mcpServerCheckbox.addActionListener(evt -> controller.changed());
        systemPanel.add(mcpServerCheckbox, UIUtils.createGbc(0, 1, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        JLabel mcpPortLabel = new JLabel(scopedLabel("LBL_McpServerPort", PluginSettings.Effect.IDE_RESTART));
        mcpPortLabel.setToolTipText(tooltip("TT_McpServerPort"));
        systemPanel.add(mcpPortLabel, UIUtils.createGbc(2, 1, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 15, 5, 5)));
        SpinnerNumberModel mcpPortModel = new SpinnerNumberModel(0, 0, 65535, 1);
        mcpPortSpinner = new JSpinner(mcpPortModel);
        mcpPortSpinner.setToolTipText(tooltip("TT_McpServerPort"));
        ((JSpinner.DefaultEditor) mcpPortSpinner.getEditor()).getTextField().setColumns(6);
        mcpPortSpinner.addChangeListener(evt -> controller.changed());
        systemPanel.add(mcpPortSpinner, UIUtils.createGbc(3, 1, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 12)));

        JLabel lineHeightLabel = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_LineHeightCorrection"));
        lineHeightLabel.setToolTipText(tooltip("TT_LineHeightCorrection"));
        systemPanel.add(lineHeightLabel, UIUtils.createGbc(2, 0, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 15, 5, 5)));

        SpinnerNumberModel lineHeightModel = new SpinnerNumberModel(1.0d, 0.5d, 3.0d, 0.1d);
        lineHeightCorrectionSpinner = new JSpinner(lineHeightModel);
        lineHeightCorrectionSpinner.setToolTipText(tooltip("TT_LineHeightCorrection"));
        ((JSpinner.DefaultEditor) lineHeightCorrectionSpinner.getEditor()).getTextField().setColumns(6);
        lineHeightCorrectionSpinner.addChangeListener(evt -> controller.changed());
        systemPanel.add(lineHeightCorrectionSpinner, UIUtils.createGbc(3, 0, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 12)));

        add(systemPanel);
        add(Box.createVerticalStrut(4));

        // --- Chat Behavior ---
        JPanel behaviorPanel = createSectionPanel("LBL_BehaviorHeader");
        behaviorPanel.setLayout(new GridBagLayout());
        row = 0;

        echoCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EchoInput"));
        echoCheckbox.setToolTipText(tooltip("TT_EchoInput"));
        echoCheckbox.addActionListener(evt -> controller.changed());
        behaviorPanel.add(echoCheckbox, UIUtils.createGbc(0, row, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 0)));

        JLabel idleTimeoutLabel = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_SessionIdleTimeout"));
        idleTimeoutLabel.setToolTipText(tooltip("TT_SessionIdleTimeout"));
        behaviorPanel.add(idleTimeoutLabel, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 15, 5, 5)));
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(600, 0, 3600, 5);
        idleTimeoutSpinner = new JSpinner(spinnerModel);
        ((JSpinner.DefaultEditor) idleTimeoutSpinner.getEditor()).getTextField().setColumns(4);
        idleTimeoutSpinner.setToolTipText(tooltip("TT_SessionIdleTimeout"));
        idleTimeoutSpinner.addChangeListener(evt -> controller.changed());
        behaviorPanel.add(idleTimeoutSpinner, UIUtils.createGbc(3, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 12)));

        combineCheckbox.setText(scopedLabel("LBL_CombineToolThought", PluginSettings.Effect.SESSION_RELOAD));
        combineCheckbox.setToolTipText(tooltip("TT_CombineToolThought"));
        combineCheckbox.addActionListener(evt -> controller.changed());
        behaviorPanel.add(combineCheckbox, UIUtils.createGbc(0, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 0)));

        JLabel maxMessagesLabel = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_MaxMessages"));
        maxMessagesLabel.setToolTipText(tooltip("TT_MaxMessages"));
        behaviorPanel.add(maxMessagesLabel, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 15, 5, 5)));
        SpinnerNumberModel maxMessagesModel = new SpinnerNumberModel(25, 10, 200, 5);
        maxMessagesSpinner = new JSpinner(maxMessagesModel);
        ((JSpinner.DefaultEditor) maxMessagesSpinner.getEditor()).getTextField().setColumns(4);
        maxMessagesSpinner.setToolTipText(tooltip("TT_MaxMessages"));
        maxMessagesSpinner.addChangeListener(evt -> controller.changed());
        behaviorPanel.add(maxMessagesSpinner, UIUtils.createGbc(3, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 12)));

        autoBackupChangesCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_AutoBackupChanges"));
        autoBackupChangesCheckbox.setToolTipText(tooltip("TT_AutoBackupChanges"));
        autoBackupChangesCheckbox.addActionListener(evt -> controller.changed());
        behaviorPanel.add(autoBackupChangesCheckbox, UIUtils.createGbc(0, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 0)));

        editPreambleButton = new JButton();
        editPreambleButton.setIcon(ThemeManager.getIcon("pencil.svg", 16));
        editPreambleButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EditPreamble"));
        editPreambleButton.setToolTipText(tooltip("TT_PreambleEditor"));
        editPreambleButton.addActionListener(evt -> openPreambleDialog());

        preambleEnabledCheckbox.setText(scopedLabel("LBL_PreambleEnabled", PluginSettings.Effect.NEW_SESSION));
        preambleEnabledCheckbox.setToolTipText(tooltip("TT_PreambleEnabled"));
        preambleEnabledCheckbox.addActionListener(evt -> {
            editPreambleButton.setEnabled(preambleEnabledCheckbox.isSelected());
            controller.changed();
        });
        behaviorPanel.add(preambleEnabledCheckbox, UIUtils.createGbc(0, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 0)));
        behaviorPanel.add(editPreambleButton, UIUtils.createGbc(3, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 12)));

        add(behaviorPanel);
        add(Box.createVerticalStrut(4));

        // --- Actions ---
        JPanel actionsPanel = createSectionPanel("LBL_ActionsHeader");
        actionsPanel.setLayout(new GridBagLayout());

        sortLinesCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_SortLines"));
        sortLinesCheckbox.setToolTipText(tooltip("TT_SortLines"));
        sortLinesCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(sortLinesCheckbox, UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        compactJsonCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_CompactJson"));
        compactJsonCheckbox.setToolTipText(tooltip("TT_CompactJson"));
        compactJsonCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(compactJsonCheckbox, UIUtils.createGbc(0, 1, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        searchWebCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_SearchWeb"));
        searchWebCheckbox.setToolTipText(tooltip("TT_SearchWeb"));
        searchWebCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(searchWebCheckbox, UIUtils.createGbc(0, 2, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        showAnnotationsCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ShowAnnotations"));
        showAnnotationsCheckbox.setToolTipText(tooltip("TT_ShowAnnotations"));
        showAnnotationsCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(showAnnotationsCheckbox, UIUtils.createGbc(0, 3, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        markdownProjectCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EnableMarkdownProject"));
        markdownProjectCheckbox.setToolTipText(tooltip("TT_EnableMarkdownProject"));
        markdownProjectCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(markdownProjectCheckbox, UIUtils.createGbc(0, 4, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        viewFileHistoryCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ViewFileHistory"));
        viewFileHistoryCheckbox.setToolTipText(tooltip("TT_ViewFileHistory"));
        viewFileHistoryCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(viewFileHistoryCheckbox, UIUtils.createGbc(1, 0, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 25, 5, 0)));

        stashDiffCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_StashDiff"));
        stashDiffCheckbox.setToolTipText(tooltip("TT_StashDiff"));
        stashDiffCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(stashDiffCheckbox, UIUtils.createGbc(1, 1, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 25, 5, 0)));

        String quickJumpShortcut = ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.GoToFileAction");
        quickJumpCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_QuickJump")
                + (quickJumpShortcut.isEmpty() ? "" : " (" + quickJumpShortcut + ")"));
        quickJumpCheckbox.setToolTipText(tooltip("TT_QuickJump"));
        quickJumpCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(quickJumpCheckbox, UIUtils.createGbc(1, 2, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 25, 5, 0)));

        copyAllItemsCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_CopyAllItems"));
        copyAllItemsCheckbox.setToolTipText(tooltip("TT_CopyAllItems"));
        copyAllItemsCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(copyAllItemsCheckbox, UIUtils.createGbc(0, 5, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        miniAssistantCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EnableMiniAssistant"));
        miniAssistantCheckbox.setToolTipText(tooltip("TT_EnableMiniAssistant"));
        miniAssistantCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(miniAssistantCheckbox, UIUtils.createGbc(1, 3, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 25, 5, 0)));

        taskRepositoryCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EnableTaskRepository"));
        taskRepositoryCheckbox.setToolTipText(tooltip("TT_EnableTaskRepository"));
        taskRepositoryCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(taskRepositoryCheckbox, UIUtils.createGbc(1, 4, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 25, 5, 0)));

        projectSwitchRestartCheckbox.setText(
                scopedLabel("LBL_ProjectSwitchRestart", PluginSettings.Effect.IDE_RESTART));
        projectSwitchRestartCheckbox.setToolTipText(tooltip("TT_ProjectSwitchRestart"));
        projectSwitchRestartCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(projectSwitchRestartCheckbox, UIUtils.createGbc(0, 6, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        // Visible without a hover: the cost of this option must be readable before it is ticked.
        JLabel projectSwitchHintLabel = new JLabel(
                NbBundle.getMessage(ACPOptionsPanel.class, "HLP_ProjectSwitchRestart"));
        projectSwitchHintLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        projectSwitchHintLabel.setFont(projectSwitchHintLabel.getFont()
                .deriveFont(Font.ITALIC, projectSwitchHintLabel.getFont().getSize() - 1f));
        GridBagConstraints projectSwitchHintGbc = UIUtils.createGbc(0, 7, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 32, 5, 0));
        projectSwitchHintGbc.gridwidth = 2;
        actionsPanel.add(projectSwitchHintLabel, projectSwitchHintGbc);

        add(actionsPanel);
        add(Box.createVerticalStrut(4));

        // --- Appearance ---
        JPanel appearancePanel = createSectionPanel("LBL_AppearanceHeader");
        appearancePanel.setLayout(new GridBagLayout());
        row = 0;

        iconLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_UserIcon"));
        iconLabel.setToolTipText(tooltip("TT_UserIcon"));
        appearancePanel.add(iconLabel, UIUtils.createGbc(0, row, 1.5, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));

        iconPathField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) {
                iconPreviewManager.updatePreview(iconPathField.getText());
                controller.changed();
            }
        });
        iconPathField.setToolTipText(tooltip("TT_UserIcon"));
        appearancePanel.add(iconPathField, UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                             GridBagConstraints.EAST, new Insets(0, 0, 5, 5)));

        iconBrowseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        iconBrowseButton.setToolTipText(tooltip("TT_UserIcon"));
        iconBrowseButton.addActionListener(evt -> iconBrowseButtonActionPerformed());
        appearancePanel.add(iconBrowseButton, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.WEST, new Insets(0, 0, 5, 0)));

        GridBagConstraints previewGbc = UIUtils.createGbc(3, 0, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.CENTER, new Insets(0, 8, 5, 12));
        previewGbc.gridheight = 3;
        appearancePanel.add(iconPreviewLabel, previewGbc);

        row++;
        JLabel toolbarIconLabel = new JLabel(scopedLabel("LBL_ToolbarIconSize",
                PluginSettings.Effect.IDE_RESTART));
        toolbarIconLabel.setToolTipText(tooltip("TT_ToolbarIconSize"));
        appearancePanel.add(toolbarIconLabel, UIUtils.createGbc(0, row, 1.5, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));

        toolbarIconCombo = new JComboBox<>(new String[]{"16", "24", "28", "32", "48"});
        toolbarIconCombo.setToolTipText(tooltip("TT_ToolbarIconSize"));
        toolbarIconCombo.setPrototypeDisplayValue("100");
        toolbarIconCombo.addActionListener(evt -> controller.changed());
        appearancePanel.add(toolbarIconCombo, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 5)));

        row++;
        JLabel chatFontLabel = new JLabel(scopedLabel("LBL_ChatFontSize", PluginSettings.Effect.SESSION_RELOAD));
        chatFontLabel.setToolTipText(tooltip("TT_ChatFontSize"));
        appearancePanel.add(chatFontLabel, UIUtils.createGbc(0, row, 1.5, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));

        chatFontCombo = new JComboBox<>(new String[]{"Inherited", "10", "11", "12", "13", "14", "16", "18", "20", "22", "24"});
        chatFontCombo.setToolTipText(tooltip("TT_ChatFontSize"));
        chatFontCombo.setPrototypeDisplayValue("100");
        chatFontCombo.addActionListener(evt -> controller.changed());
        appearancePanel.add(chatFontCombo, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 5)));

        add(appearancePanel);
        add(Box.createVerticalStrut(4));
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
        String currentPath = getExecutablePath();
        if (!currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                chooser.setSelectedFile(currentFile);
            }
        }
        chooser.setDialogTitle(NbBundle.getMessage(ACPOptionsPanel.class, "TITLE_SelectExecutable"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            userEditedPath = true;
            pathCombo.getEditor().setItem(chooser.getSelectedFile().getAbsolutePath());
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

    private void openPreambleDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        PreambleDialog dialog = new PreambleDialog(owner, preambleText);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            preambleText = dialog.getPreambleText();
            controller.changed();
        }
    }

    public void load() {
        userEditedPath = false;
        String savedPath = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get(PreferenceKeys.ACP_EXECUTABLE_PATH, null);

        // Discover known binaries on PATH
        boolean useWsl = BinaryResolver.isWslAvailable();
        String[] wslCandidates = BinaryResolver.linuxHarnessNames();
        String[] nativeCandidates = BinaryResolver.nativeHarnessNames();
        pathLabels.clear();
        pathCombo.removeAllItems();
        String detectedFirst = null;
        // Native Windows/hosted binaries
        for (String name : nativeCandidates) {
            String found = BinaryResolver.findOnPath(name);
            if (found != null) {
                String path = useWsl ? BinaryResolver.toWslPath(found) : found;
                pathCombo.addItem(path);
                pathLabels.put(path, path);
                if (detectedFirst == null) {
                    detectedFirst = path;
                }
            }
        }
        // WSL-native binaries (inside the distro)
        if (useWsl) {
            for (String name : wslCandidates) {
                String found = BinaryResolver.findOnWslPath(name);
                if (found != null) {
                    // Skip if already present (native + WSL could resolve to the same path)
                    boolean alreadyAdded = false;
                    for (int i = 0; i < pathCombo.getItemCount(); i++) {
                        if (found.equals(pathCombo.getItemAt(i))) {
                            alreadyAdded = true;
                            break;
                        }
                    }
                    if (!alreadyAdded) {
                        pathCombo.addItem(found);
                        pathLabels.put(found, found + " (WSL)");
                        if (detectedFirst == null) {
                            detectedFirst = found;
                        }
                    }
                }
            }
        }

        if (savedPath != null && !savedPath.isEmpty()) {
            // Convert Windows path to WSL mount path when WSL mode is on
            String matchPath = useWsl ? BinaryResolver.toWslPath(savedPath) : savedPath;
            // Ensure the saved path is in the combo (may be a manual entry)
            boolean found = false;
            for (int i = 0; i < pathCombo.getItemCount(); i++) {
                if (matchPath.equals(pathCombo.getItemAt(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                pathCombo.insertItemAt(matchPath, 0);
                pathLabels.put(matchPath, matchPath);
            }
            pathCombo.setSelectedItem(matchPath);
        } else if (detectedFirst != null) {
            pathCombo.setSelectedItem(detectedFirst);
        } else {
            pathCombo.getEditor().setItem("");
        }

        argsField.setText(NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get(PreferenceKeys.PROCESS_ARGUMENTS, "acp"));

        preambleText = PluginSettings.getPreamble();
        echoCheckbox.setSelected(PluginSettings.isEchoUserInput());
        combineCheckbox.setSelected(PluginSettings.isCombineToolThoughtEnabled());
        autoBackupChangesCheckbox.setSelected(PluginSettings.isAutoBackupChanges());
        checkForUpdatesCheckbox.setSelected(PluginSettings.isCheckForUpdates());
        Preferences editorPrefs = MimeLookup.getLookup(MimePath.EMPTY).lookup(Preferences.class);
        lineHeightCorrectionSpinner.setValue(editorPrefs.getFloat(SimpleValueNames.LINE_HEIGHT_CORRECTION, 1.0f));
        preambleEnabledCheckbox.setSelected(PluginSettings.isPreambleEnabled());
        editPreambleButton.setEnabled(preambleEnabledCheckbox.isSelected());
        idleTimeoutSpinner.setValue(PluginSettings.getSessionIdleTimeout());
        maxMessagesSpinner.setValue(PluginSettings.getMaxMessages());
        previousIconPath = PluginSettings.getCustomUserIcon();
        iconPathField.setText(previousIconPath);
        iconPreviewManager.updatePreview(iconPathField.getText());

        int currentSize = PluginSettings.getToolbarIconSize();
        toolbarIconCombo.setSelectedItem(String.valueOf(currentSize));

        int currentChatFont = PluginSettings.getChatFontSize();
        chatFontCombo.setSelectedItem(currentChatFont < 0 ? "Inherited" : String.valueOf(currentChatFont));

        sortLinesCheckbox.setSelected(PluginSettings.isSortLinesEnabled());
        compactJsonCheckbox.setSelected(PluginSettings.isCompactJsonEnabled());
        searchWebCheckbox.setSelected(PluginSettings.isSearchWebEnabled());
        showAnnotationsCheckbox.setSelected(PluginSettings.isShowAnnotationsEnabled());
        viewFileHistoryCheckbox.setSelected(PluginSettings.isViewFileHistoryEnabled());
        stashDiffCheckbox.setSelected(PluginSettings.isStashDiffEnabled());
        quickJumpCheckbox.setSelected(PluginSettings.isQuickJumpEnabled());
        copyAllItemsCheckbox.setSelected(PluginSettings.isCopyAllItemsEnabled());
        miniAssistantCheckbox.setSelected(PluginSettings.isMiniAssistantEnabled());
        taskRepositoryCheckbox.setSelected(PluginSettings.isTaskRepositoryEnabled());
        projectSwitchRestartCheckbox.setSelected(PluginSettings.isProjectSwitchRestartConfigured());
        markdownProjectCheckbox.setSelected(PluginSettings.isMarkdownProjectEnabled());
        mcpServerCheckbox.setSelected(PluginSettings.isMcpServerEnabled());
        mcpPortSpinner.setValue(PluginSettings.getMcpServerPort());
        useWslCheckbox.setSelected(PluginSettings.isUseWsl());
        updateGeminiModelHint();
        // Reopening Options (including after Cancel) must still show what is outstanding.
        refreshPendingRestartBar();
    }

    /** Shows a reminder to change --model when the selected binary is Gemini. */
    private void updateGeminiModelHint() {
        String binName = BinaryResolver.binaryNameFromPath(getExecutablePath());
        HarnessCatalog.Harness h = binName != null ? HarnessCatalog.byBinaryName(binName) : null;
        if (h != null && "gemini".equals(h.id())) {
            argsHintLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "MSG_GeminiDefaultModel"));
            argsHintLabel.setVisible(true);
        } else {
            argsHintLabel.setText("");
            argsHintLabel.setVisible(false);
        }
    }

    /** Returns the currently selected/entered executable path from the combo. */
    private String getExecutablePath() {
        Object item = pathCombo.getSelectedItem();
        return item != null ? item.toString().trim() : "";
    }

    /**
     * Permanent scope marker for an option whose value the running IDE only reads at
     * startup (or at session creation). Appended to the control's own text so it is part
     * of the accessible name and mirrors under RTL.
     */
    private static String scopeSuffix(PluginSettings.Effect effect) {
        return switch (effect) {
            case IDE_RESTART -> NbBundle.getMessage(ACPOptionsPanel.class, "SCOPE_Restart");
            case NEW_SESSION -> NbBundle.getMessage(ACPOptionsPanel.class, "SCOPE_NewSession");
            case SESSION_RELOAD -> NbBundle.getMessage(ACPOptionsPanel.class, "SCOPE_SessionReload");
            case IMMEDIATE -> "";
        };
    }

    /** Bundle label plus its permanent scope marker. */
    private static String scopedLabel(String key, PluginSettings.Effect effect) {
        return NbBundle.getMessage(ACPOptionsPanel.class, key) + scopeSuffix(effect);
    }

    /** Bundle-backed tooltip, wrapped so long hints stay readable. */
    private static String tooltip(String key) {
        return wrapTooltip(NbBundle.getMessage(ACPOptionsPanel.class, key));
    }

    /**
     * Wraps a tooltip into HTML at roughly {@link #TOOLTIP_CHARS} columns. Values that
     * already start with {@code <html>} pass through untouched — nesting a second
     * {@code <body>} would drop the width. Newlines become line breaks, and long
     * unbroken tokens (paths, commands) get zero-width breaks because Swing's HTML
     * view cannot break a word.
     */
    static String wrapTooltip(String raw) {
        if (raw == null || raw.isEmpty() || raw.startsWith("<html>")) {
            return raw;
        }
        String escaped = XmlUtils.escapeHtml(raw).replace("\n", "<br>");
        return "<html><body style='width:" + tooltipWidth() + "px'>"
                + breakLongTokens(escaped) + "</body></html>";
    }

    /** Inserts zero-width breaks into over-long tokens so Swing can wrap them. */
    private static String breakLongTokens(String text) {
        Matcher matcher = LONG_TOKEN.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 16);
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(breakToken(matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String breakToken(String token) {
        if (token.indexOf('&') >= 0 || token.indexOf(';') >= 0) {
            return token;
        }
        StringBuilder out = new StringBuilder(token.length() + 8);
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            out.append(c);
            boolean separator = c == '/' || c == '-' || c == '@' || c == '.';
            if (separator && i < token.length() - 1) {
                out.append(ZERO_WIDTH_SPACE);
            }
        }
        return out.toString();
    }

    /** Tooltip width in pixels, measured from the LAF tooltip font and cached per font. */
    private static int tooltipWidth() {
        Font font = UIManager.getFont("ToolTip.font");
        if (font == null) {
            font = UIManager.getFont("Label.font");
        }
        if (font != null && font.equals(cachedTooltipFont)) {
            return cachedTooltipWidth;
        }
        int charWidth = 7;
        if (font != null) {
            charWidth = Math.max(1, new JLabel().getFontMetrics(font).stringWidth("x"));
        }
        cachedTooltipFont = font;
        cachedTooltipWidth = Math.max(MIN_TOOLTIP_WIDTH, charWidth * TOOLTIP_CHARS);
        return cachedTooltipWidth;
    }

    /**
     * Builds the transient "a restart is pending" bar. Text only: an IDE restart is not
     * data loss, so the bar stays calm instead of turning into a warning (sessions live in
     * the store and reload through {@code session/load}).
     */
    private void createPendingRestartBar() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        pendingRestartBar = new JPanel();
        pendingRestartBar.setLayout(new BoxLayout(pendingRestartBar, BoxLayout.Y_AXIS));
        pendingRestartBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        pendingRestartBar.setOpaque(true);
        pendingRestartBar.setBackground(theme.sunkenBackground());
        pendingRestartBar.setBorder(new EmptyBorder(6, 12, 6, 12));

        pendingRestartTitle = new JLabel();
        pendingRestartTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        pendingRestartBody = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "RST_Pending_Body"));
        pendingRestartBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        pendingRestartBody.setForeground(theme.mutedForeground());
        pendingRestartBody.setFont(pendingRestartBody.getFont()
                .deriveFont(pendingRestartBody.getFont().getSize() - 1f));

        pendingRestartBar.add(pendingRestartTitle);
        pendingRestartBar.add(pendingRestartBody);
        pendingRestartBar.setVisible(false);
        add(pendingRestartBar);
        add(Box.createVerticalStrut(8));
    }

    /** Recomputes pending restarts from stored vs effective values and updates the bar. */
    private void refreshPendingRestartBar() {
        List<String> pending = PluginSettings.getAllPendingRestartKeys();
        boolean visible = !pending.isEmpty();
        if (visible) {
            pendingRestartTitle.setText(pendingBarText(pending));
            pendingRestartBar.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    pendingRestartBar.getPreferredSize().height));
        }
        pendingRestartBar.setVisible(visible);
        revalidate();
        repaint();
    }

    private static String pendingBarText(List<String> keys) {
        String first = pendingItemLabel(keys.get(0));
        if (keys.size() == 1) {
            return NbBundle.getMessage(ACPOptionsPanel.class, "RST_Pending_Single", first);
        }
        return NbBundle.getMessage(ACPOptionsPanel.class, "RST_Pending_Multiple", first, keys.size() - 1);
    }

    /** Short display name for a restart-scoped preference inside the bar. */
    private static String pendingItemLabel(String key) {
        String bundleKey = switch (key) {
            case PreferenceKeys.ACP_EXECUTABLE_PATH -> "RST_Item_Path";
            case PreferenceKeys.PROCESS_ARGUMENTS -> "RST_Item_Args";
            case PreferenceKeys.USE_WSL -> "RST_Item_Wsl";
            case PreferenceKeys.TOOLBAR_ICON_SIZE -> "RST_Item_IconSize";
            case PreferenceKeys.PROJECT_SWITCH_RESTART_ENABLED -> "RST_Item_ProjectSwitch";
            case PreferenceKeys.MCP_SERVER_ENABLED -> "RST_Item_McpEnabled";
            case PreferenceKeys.MCP_SERVER_PORT -> "RST_Item_McpPort";
            default -> null;
        };
        return bundleKey == null ? key : NbBundle.getMessage(ACPOptionsPanel.class, bundleKey);
    }

    public void store() {
        String pathToSave = getExecutablePath();
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(PreferenceKeys.ACP_EXECUTABLE_PATH, pathToSave);
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(PreferenceKeys.PROCESS_ARGUMENTS, argsField.getText());
        // Persist harness ID so the tooltip/icon can identify the agent
        // even when acpExecutablePath is not resolvable (e.g. WSL paths).
        String binName = BinaryResolver.binaryNameFromPath(pathToSave);
        HarnessCatalog.Harness h = binName != null ? HarnessCatalog.byBinaryName(binName) : null;
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(
                PreferenceKeys.ACP_HARNESS_ID, h != null ? h.id() : "");
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(
                PreferenceKeys.ACP_HARNESS_DISPLAY_NAME, h != null ? h.displayName() : "");
        if (PluginSettings.isPreambleTooLong(preambleText)) {
            // One preference value is capped at 8192 chars. Keep the previously
            // stored preamble and explain, instead of letting put() throw out of store().
            warnPreambleTooLong(preambleText.length());
        } else {
            PluginSettings.setPreamble(preambleText);
        }
        PluginSettings.setPreambleEnabled(preambleEnabledCheckbox.isSelected());
        PluginSettings.setEchoUserInput(echoCheckbox.isSelected());
        PluginSettings.setCombineToolThoughtEnabled(combineCheckbox.isSelected());
        PluginSettings.setAutoBackupChanges(autoBackupChangesCheckbox.isSelected());
        PluginSettings.setCheckForUpdates(checkForUpdatesCheckbox.isSelected());
        Preferences editorPrefs = MimeLookup.getLookup(MimePath.EMPTY).lookup(Preferences.class);
        editorPrefs.putFloat(SimpleValueNames.LINE_HEIGHT_CORRECTION, ((Number) lineHeightCorrectionSpinner.getValue()).floatValue());
        PluginSettings.setSessionIdleTimeout((Integer) idleTimeoutSpinner.getValue());
        PluginSettings.setMaxMessages((Integer) maxMessagesSpinner.getValue());
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
        PluginSettings.setCompactJsonEnabled(compactJsonCheckbox.isSelected());
        PluginSettings.setSearchWebEnabled(searchWebCheckbox.isSelected());
        PluginSettings.setShowAnnotationsEnabled(showAnnotationsCheckbox.isSelected());
        PluginSettings.setViewFileHistoryEnabled(viewFileHistoryCheckbox.isSelected());
        PluginSettings.setStashDiffEnabled(stashDiffCheckbox.isSelected());
        PluginSettings.setQuickJumpEnabled(quickJumpCheckbox.isSelected());
        PluginSettings.setCopyAllItemsEnabled(copyAllItemsCheckbox.isSelected());
        PluginSettings.setMiniAssistantEnabled(miniAssistantCheckbox.isSelected());
        PluginSettings.setTaskRepositoryEnabled(taskRepositoryCheckbox.isSelected());
        PluginSettings.setProjectSwitchRestartEnabled(projectSwitchRestartCheckbox.isSelected());
        PluginSettings.setMarkdownProjectEnabled(markdownProjectCheckbox.isSelected());
        PluginSettings.setMcpServerEnabled(mcpServerCheckbox.isSelected());
        PluginSettings.setMcpServerPort((Integer) mcpPortSpinner.getValue());
        PluginSettings.setUseWsl(useWslCheckbox.isSelected());

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
        // Recompute here: every restart-scoped value has been stored by now, and the
        // comparison against the startup snapshot is what decides the bar.
        refreshPendingRestartBar();
    }

    /**
     * Tells the user why the edited preamble was not stored. Non-modal on purpose:
     * the rest of the Options save still applies, and the previous preamble is kept.
     */
    private void warnPreambleTooLong(int length) {
        String title = NbBundle.getMessage(ACPOptionsPanel.class, "LBL_PreambleTooLong");
        String message = NbBundle.getMessage(ACPOptionsPanel.class, "MSG_PreambleTooLong",
                length, PluginSettings.MAX_PREAMBLE_LENGTH);
        LOG.log(java.util.logging.Level.WARNING, "Session preamble not saved: {0} characters", length);
        SwingUtilities.invokeLater(() -> NotificationDisplayer.getDefault().notify(
                title, NotificationDisplayer.Priority.LOW.getIcon(), message, null,
                NotificationDisplayer.Priority.LOW));
    }

    public boolean valid() {
        if (!userEditedPath) {
            pathErrorLabel.setText("");
            return true;
        }
        String path = getExecutablePath();
        if (isBlank(path)) {
            pathErrorLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "ERR_EmptyPath"));
            pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            return false;
        }
        // WSL-internal paths (e.g. /usr/local/bin/goose) never exist on the
        // Windows filesystem. Accept them under the same condition the runtime
        // resolver uses (BinaryResolver.findExecutablePathOrNull) so the
        // options panel does not block valid WSL launches.
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (path.startsWith("/") && isWindows) {
            if (!BinaryResolver.isWslAvailable()) {
                pathErrorLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "ERR_WslNotAvailable"));
                pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
                return false;
            }
            pathErrorLabel.setText("");
            return true;
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
        if (isWindows) {
            String lower = path.toLowerCase();
            if (!lower.endsWith(".exe") && !lower.endsWith(".cmd") && !lower.endsWith(".bat")) {
                pathErrorLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "ERR_MissingExeExtension"));
                pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
                return false;
            }
        }
        pathErrorLabel.setText("");
        return true;
    }

    /** Renderer that shows WSL suffix on WSL-native entries. */
    private class PathComboRenderer extends JLabel implements ListCellRenderer<String> {
        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                int index, boolean isSelected, boolean cellHasFocus) {
            setText(value != null ? pathLabels.getOrDefault(value, value) : "");
            setFont(list.getFont());
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            setOpaque(true);
            return this;
        }
    }

}
