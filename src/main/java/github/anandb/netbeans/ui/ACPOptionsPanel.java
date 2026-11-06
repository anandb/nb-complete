package github.anandb.netbeans.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.AcpFsPolicy;
import github.anandb.netbeans.support.HarnessSelection;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.XmlUtils;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.SimpleValueNames;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
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

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.isBlank;
import github.anandb.netbeans.support.PreferenceKeys;
import org.openide.awt.NotificationDisplayer;
import github.anandb.netbeans.ui.permission.ContextActionsDialog;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import github.anandb.netbeans.ui.rendering.ColorTheme;
import github.anandb.netbeans.ui.rendering.IconPreviewManager;
import github.anandb.netbeans.ui.platform.SessionService;
import github.anandb.netbeans.ui.platform.PlatformBridge;

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
    /** WSL probes spawn a process each, so they run here rather than on the EDT. */
    private static final RequestProcessor DISCOVERY_RP =
            new RequestProcessor("ACP-Options-BinaryDiscovery", 1); // NOI18N
    /** Invalidates a probe whose results would land after a newer {@link #load()}. */
    private volatile int wslDiscoveryGeneration;
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
    private JPanel projectSwitchHint;
    private JLabel contextActionsSummary;
    private JButton contextActionsButton;
    private JSpinner idleTimeoutSpinner;
    private JSpinner maxMessagesSpinner;
    private JLabel argsLabel;
    private JTextField argsField;
    private JLabel argsHintLabel;
    private JComboBox<HarnessSelection.Selection> harnessCombo;
    private JLabel harnessHintLabel;
    private JButton harnessUseDetectedButton;
    private JPanel harnessHintRow;
    private JLabel iconLabel;
    private JTextField iconPathField;
    private JButton iconBrowseButton;
    private JLabel iconPreviewLabel;
    private JPanel pendingRestartBar;
    private JLabel pendingRestartTitle;
    private JLabel pendingRestartBody;
    private transient IconPreviewManager iconPreviewManager;

    /** Starting width of the square user-icon preview; its height comes from the three rows. */
    private static final int PREVIEW_SIZE = 96;
    private JComboBox<String> toolbarIconCombo;
    private JComboBox<String> chatFontCombo;
    private JButton editPreambleButton;
    private JCheckBox preambleEnabledCheckbox;
    private JComboBox<AcpFsPolicy> fsPolicyCombo;
    private JButton runCommandPolicyButton;
    private String runCommandAllowlist = "";
    private String runCommandDenylist = "";
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
        add(createScopeLegend());
        add(Box.createVerticalStrut(2));

        // Two tabs: the handful of controls a normal user needs, and the expert knobs
        // (server ports, limits, restart policy) behind them. Same controls, same store().
        JPanel essentialsTab = newTabPanel();
        JPanel advancedTab = newTabPanel();
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab(NbBundle.getMessage(UIBundle.class, "LBL_TabEssentials"), essentialsTab);
        tabbedPane.addTab(NbBundle.getMessage(UIBundle.class, "LBL_TabAdvanced"), advancedTab);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        add(tabbedPane);

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
        iconPathField = new JTextField(26);
        iconBrowseButton = new JButton();
        iconPreviewLabel = new JLabel();

        iconPreviewManager = new IconPreviewManager(iconPreviewLabel, iconPathField, PREVIEW_SIZE,
                controller::changed);

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
                onPathChanged();
            }
        });
        pathCombo.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) {
                userEditedPath = true;
                onPathChanged();
            }
        });
        servicePanel.add(pathCombo, UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        browseButton.setText(NbBundle.getMessage(UIBundle.class, "BTN_Browse"));
        browseButton.setToolTipText(tooltip("TT_BrowseExecutable"));
        browseButton.addActionListener(evt -> browseButtonActionPerformed());
        servicePanel.add(browseButton, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                            new Insets(0, 0, 5, 0)));

        pathErrorLabel = new JLabel();
        pathErrorLabel.setForeground(UIManager.getColor("Panel.foreground"));
        pathErrorLabel.setFont(pathErrorLabel.getFont().deriveFont(Font.ITALIC, pathErrorLabel.getFont().getSize() - 1f));
        // Hidden while empty: an invisible row must not open a gap the other rows do not have.
        pathErrorLabel.setVisible(false);
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
                                         GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        argsHintLabel = new JLabel();
        argsHintLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        argsHintLabel.setFont(argsHintLabel.getFont().deriveFont(Font.ITALIC, argsHintLabel.getFont().getSize() - 1f));
        GridBagConstraints hintGbc = UIUtils.createGbc(1, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 0, 5, 5));
        hintGbc.gridwidth = 2;
        servicePanel.add(argsHintLabel, hintGbc);

        // A container launcher (docker/podman) can never name the harness inside the image,
        // so the user's explicit choice is the normal path there. Placed after the args
        // because those arguments belong to the launcher, not to the harness.
        harnessCombo = new JComboBox<>();
        harnessCombo.addItem(new HarnessSelection.Selection(HarnessCatalog.UNKNOWN, true));
        for (HarnessCatalog.Harness harness : HarnessCatalog.sortedByName()) {
            harnessCombo.addItem(new HarnessSelection.Selection(harness, false));
        }
        harnessCombo.setRenderer(new HarnessChoiceRenderer());
        harnessCombo.setToolTipText(tooltip("TT_Harness"));
        harnessCombo.addActionListener(evt -> {
            refreshHarnessHint();
            controller.changed();
        });
        JLabel harnessLabel = new JLabel(scopedLabel("LBL_Harness", PluginSettings.Effect.IDE_RESTART));
        harnessLabel.setToolTipText(tooltip("TT_Harness"));
        harnessLabel.setLabelFor(harnessCombo);
        servicePanel.add(harnessLabel, UIUtils.createGbc(0, ++row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));
        servicePanel.add(harnessCombo, UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        harnessHintRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        harnessHintRow.setOpaque(false);
        harnessHintLabel = new JLabel();
        harnessHintLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        harnessHintLabel.setFont(harnessHintLabel.getFont()
                .deriveFont(Font.ITALIC, harnessHintLabel.getFont().getSize() - 1f));
        harnessUseDetectedButton = new JButton();
        harnessUseDetectedButton.setBorderPainted(false);
        harnessUseDetectedButton.setContentAreaFilled(false);
        harnessUseDetectedButton.setFocusPainted(false);
        harnessUseDetectedButton.setMargin(new Insets(0, 0, 0, 0));
        harnessUseDetectedButton.setForeground(UIManager.getColor("Label.foreground"));
        harnessUseDetectedButton.addActionListener(evt -> useDetectedHarness());
        harnessHintRow.add(harnessHintLabel);
        harnessHintRow.add(harnessUseDetectedButton);
        GridBagConstraints harnessHintGbc = UIUtils.createGbc(1, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 0, 5, 5));
        harnessHintGbc.gridwidth = 2;
        servicePanel.add(harnessHintRow, harnessHintGbc);

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

        addSection(essentialsTab, servicePanel);

        // --- System ---
        JPanel systemPanel = createSectionPanel("LBL_SystemHeader");
        systemPanel.setLayout(new GridBagLayout());

        checkForUpdatesCheckbox.setText(NbBundle.getMessage(UIBundle.class, "LBL_CheckForUpdates"));
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

        JLabel lineHeightLabel = new JLabel(NbBundle.getMessage(UIBundle.class, "LBL_LineHeightCorrection"));
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

        JLabel fsLabel = new JLabel(NbBundle.getMessage(UIBundle.class, "LBL_FsLabel"));
        fsLabel.setToolTipText(tooltip("TT_FsNote"));
        systemPanel.add(fsLabel, UIUtils.createGbc(2, 2, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 15, 5, 5)));

        fsPolicyCombo = new JComboBox<>(AcpFsPolicy.values());
        fsPolicyCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value instanceof AcpFsPolicy policy ? fsPolicyLabel(policy) : "");
                return this;
            }
        });
        fsPolicyCombo.setPrototypeDisplayValue(AcpFsPolicy.READ_ANY_WRITE_PROJECT);
        fsPolicyCombo.addActionListener(evt -> {
            updateFsPolicyTooltip();
            controller.changed();
        });
        systemPanel.add(fsPolicyCombo, UIUtils.createGbc(3, 2, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 0, 12)));

        runCommandPolicyButton = new JButton(
                NbBundle.getMessage(UIBundle.class, "LBL_RunCommandPolicy"));
        runCommandPolicyButton.setToolTipText(tooltip("TT_RunCommandPolicy"));
        runCommandPolicyButton.addActionListener(evt -> editRunCommandPolicy());
        systemPanel.add(runCommandPolicyButton, UIUtils.createGbc(3, 3, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.NORTHEAST, new Insets(4, 0, 5, 12)));

        projectSwitchRestartCheckbox.setText(
                scopedLabel("LBL_ProjectSwitchRestart", PluginSettings.Effect.IDE_RESTART));
        projectSwitchRestartCheckbox.setToolTipText(tooltip("TT_ProjectSwitchRestart"));
        projectSwitchRestartCheckbox.addActionListener(evt -> controller.changed());
        GridBagConstraints restartGbc = UIUtils.createGbc(0, 2, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.SOUTHWEST, new Insets(0, 12, 1, 0));
        restartGbc.gridwidth = 2;
        systemPanel.add(projectSwitchRestartCheckbox, restartGbc);

        // Visible without a hover: the cost of this option must be readable before it is ticked.
        // Two lines, so the whole sentence reads instead of being cut off at the button.
        String restartHint = NbBundle.getMessage(UIBundle.class, "HLP_ProjectSwitchRestart");
        projectSwitchHint = new JPanel();
        projectSwitchHint.setOpaque(false);
        projectSwitchHint.setLayout(new BoxLayout(projectSwitchHint, BoxLayout.Y_AXIS));
        projectSwitchHint.setToolTipText(restartHint);
        for (String hintLine : splitHint(restartHint)) {
            JLabel line = new JLabel(hintLine);
            line.setForeground(UIManager.getColor("Label.disabledForeground"));
            line.setFont(line.getFont().deriveFont(Font.ITALIC, line.getFont().getSize() - 1f));
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            projectSwitchHint.add(line);
        }
        // Start under the checkbox text, not under the check glyph: the icon width and gap
        // differ per LAF, so a fixed indent drifts with the look and feel.
        int hintIndent = 12 + checkboxTextInset(projectSwitchRestartCheckbox);
        GridBagConstraints projectSwitchHintGbc = UIUtils.createGbc(0, 3, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.NORTHWEST, new Insets(0, hintIndent, 5, 0));
        projectSwitchHintGbc.gridwidth = 3;
        systemPanel.add(projectSwitchHint, projectSwitchHintGbc);

        // One width for the right-hand controls: the two spinners match the
        // ACP file-access dropdown instead of rendering as narrow boxes.
        int rightColumnWidth = fsPolicyCombo.getPreferredSize().width;
        for (JSpinner spinner : new JSpinner[]{lineHeightCorrectionSpinner, mcpPortSpinner}) {
            Dimension spinnerSize = spinner.getPreferredSize();
            spinner.setPreferredSize(new Dimension(rightColumnWidth, spinnerSize.height));
        }
        Dimension whitelistSize = runCommandPolicyButton.getPreferredSize();
        runCommandPolicyButton.setPreferredSize(
                new Dimension(rightColumnWidth, whitelistSize.height));

        addSection(advancedTab, systemPanel);

        // --- Chat Behavior (essentials) ---
        JPanel behaviorPanel = createSectionPanel("LBL_BehaviorHeader");
        behaviorPanel.setLayout(new GridBagLayout());
        row = 0;

        echoCheckbox.setText(NbBundle.getMessage(UIBundle.class, "LBL_EchoInput"));
        echoCheckbox.setToolTipText(tooltip("TT_EchoInput"));
        echoCheckbox.addActionListener(evt -> controller.changed());
        behaviorPanel.add(echoCheckbox, UIUtils.createGbc(0, row, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 0)));

        combineCheckbox.setText(scopedLabel("LBL_CombineToolThought", PluginSettings.Effect.SESSION_RELOAD));
        combineCheckbox.setToolTipText(tooltip("TT_CombineToolThought"));
        combineCheckbox.addActionListener(evt -> controller.changed());
        behaviorPanel.add(combineCheckbox, UIUtils.createGbc(0, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 0)));

        editPreambleButton = new JButton();
        editPreambleButton.setIcon(ThemeManager.getIcon("pencil.svg", 16));
        editPreambleButton.setText(NbBundle.getMessage(UIBundle.class, "LBL_EditPreamble"));
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

        addSection(essentialsTab, behaviorPanel);

        // --- Session Limits (advanced) ---
        JPanel limitsPanel = createSectionPanel("LBL_LimitsHeader");
        limitsPanel.setLayout(new GridBagLayout());
        row = 0;

        JLabel idleTimeoutLabel = new JLabel(NbBundle.getMessage(UIBundle.class, "LBL_SessionIdleTimeout"));
        idleTimeoutLabel.setToolTipText(tooltip("TT_SessionIdleTimeout"));
        limitsPanel.add(idleTimeoutLabel, UIUtils.createGbc(0, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(600, 0, 3600, 5);
        idleTimeoutSpinner = new JSpinner(spinnerModel);
        ((JSpinner.DefaultEditor) idleTimeoutSpinner.getEditor()).getTextField().setColumns(4);
        idleTimeoutSpinner.setToolTipText(tooltip("TT_SessionIdleTimeout"));
        idleTimeoutSpinner.addChangeListener(evt -> controller.changed());
        limitsPanel.add(idleTimeoutSpinner, UIUtils.createGbc(3, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 12)));

        JLabel maxMessagesLabel = new JLabel(NbBundle.getMessage(UIBundle.class, "LBL_MaxMessages"));
        maxMessagesLabel.setToolTipText(tooltip("TT_MaxMessages"));
        limitsPanel.add(maxMessagesLabel, UIUtils.createGbc(0, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));
        SpinnerNumberModel maxMessagesModel = new SpinnerNumberModel(25, 10, 200, 5);
        maxMessagesSpinner = new JSpinner(maxMessagesModel);
        ((JSpinner.DefaultEditor) maxMessagesSpinner.getEditor()).getTextField().setColumns(4);
        maxMessagesSpinner.setToolTipText(tooltip("TT_MaxMessages"));
        maxMessagesSpinner.addChangeListener(evt -> controller.changed());
        limitsPanel.add(maxMessagesSpinner, UIUtils.createGbc(3, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 12)));

        autoBackupChangesCheckbox.setText(NbBundle.getMessage(UIBundle.class, "LBL_AutoBackupChanges"));
        autoBackupChangesCheckbox.setToolTipText(tooltip("TT_AutoBackupChanges"));
        autoBackupChangesCheckbox.addActionListener(evt -> controller.changed());
        GridBagConstraints backupGbc = UIUtils.createGbc(0, ++row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0));
        backupGbc.gridwidth = 4;
        limitsPanel.add(autoBackupChangesCheckbox, backupGbc);

        addSection(advancedTab, limitsPanel);

        // --- IDE Extensions ---
        JPanel actionsPanel = createSectionPanel("LBL_ActionsHeader");
        actionsPanel.setLayout(new GridBagLayout());

        markdownProjectCheckbox.setText(NbBundle.getMessage(UIBundle.class, "LBL_EnableMarkdownProject"));
        markdownProjectCheckbox.setToolTipText(tooltip("TT_EnableMarkdownProject"));
        markdownProjectCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(markdownProjectCheckbox, UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        miniAssistantCheckbox.setText(NbBundle.getMessage(UIBundle.class, "LBL_EnableMiniAssistant"));
        miniAssistantCheckbox.setToolTipText(tooltip("TT_EnableMiniAssistant"));
        miniAssistantCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(miniAssistantCheckbox, UIUtils.createGbc(1, 0, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 12)));

        taskRepositoryCheckbox.setText(NbBundle.getMessage(UIBundle.class, "LBL_EnableTaskRepository"));
        taskRepositoryCheckbox.setToolTipText(tooltip("TT_EnableTaskRepository"));
        taskRepositoryCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(taskRepositoryCheckbox, UIUtils.createGbc(0, 1, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        contextActionsSummary = new JLabel();
        contextActionsSummary.setToolTipText(tooltip("TT_ContextActions"));
        actionsPanel.add(contextActionsSummary, UIUtils.createGbc(0, 2, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));

        contextActionsButton = new JButton(NbBundle.getMessage(UIBundle.class, "LBL_Configure"));
        contextActionsButton.setToolTipText(tooltip("TT_ContextActions"));
        contextActionsButton.addActionListener(evt -> editContextActions());
        actionsPanel.add(contextActionsButton, UIUtils.createGbc(1, 2, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 12)));

        addSection(advancedTab, actionsPanel);

        // --- Appearance ---
        JPanel appearancePanel = createSectionPanel("LBL_AppearanceHeader");
        appearancePanel.setLayout(new GridBagLayout());
        row = 0;

        iconLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_UserIcon"));
        iconLabel.setToolTipText(tooltip("TT_UserIcon"));
        appearancePanel.add(iconLabel, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.HORIZONTAL,
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

        iconBrowseButton.setText(NbBundle.getMessage(UIBundle.class, "BTN_Browse"));
        iconBrowseButton.setToolTipText(tooltip("TT_UserIcon"));
        iconBrowseButton.addActionListener(evt -> iconBrowseButtonActionPerformed());
        appearancePanel.add(iconBrowseButton, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.EAST, new Insets(0, 0, 5, 5)));

        GridBagConstraints previewGbc = UIUtils.createGbc(3, 0, 0.0, 0, GridBagConstraints.VERTICAL,
                GridBagConstraints.CENTER, new Insets(0, 8, 5, 12));
        previewGbc.gridheight = 3;
        appearancePanel.add(iconPreviewLabel, previewGbc);
        row++;
        JLabel toolbarIconLabel = new JLabel(scopedLabel("LBL_ToolbarIconSize",
                PluginSettings.Effect.IDE_RESTART));
        toolbarIconLabel.setToolTipText(tooltip("TT_ToolbarIconSize"));
        appearancePanel.add(toolbarIconLabel, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.HORIZONTAL,
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
        appearancePanel.add(chatFontLabel, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));

        chatFontCombo = new JComboBox<>(new String[]{"Inherited", "10", "11", "12", "13", "14", "16", "18", "20", "22", "24"});
        chatFontCombo.setToolTipText(tooltip("TT_ChatFontSize"));
        // "Inherited" is the widest value and the stored default, so it sets the width.
        chatFontCombo.setPrototypeDisplayValue("Inherited");
        chatFontCombo.addActionListener(evt -> controller.changed());
        appearancePanel.add(chatFontCombo, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 5)));

        addSection(essentialsTab, appearancePanel);
        finishTab(essentialsTab);
        finishTab(advancedTab);
    }

    private static final int SECTION_GAP = 6;

    /** Tab content: sections stacked top-down at their natural height, spare space at the bottom. */
    private static JPanel newTabPanel() {
        JPanel tab = new JPanel(new GridBagLayout());
        tab.setBorder(BorderFactory.createEmptyBorder(6, 2, 2, 2));
        return tab;
    }

    /** Appends one titled section to a tab, full width, in insertion order. */
    private static void addSection(JPanel tab, JPanel section) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = tab.getComponentCount();
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, SECTION_GAP, 0);
        tab.add(section, gbc);
    }

    /** Hands the remaining height to one filler, so the sections keep their preferred height. */
    private static void finishTab(JPanel tab) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = tab.getComponentCount();
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        tab.add(Box.createGlue(), gbc);
    }

    private JPanel createSectionPanel(String headerKey) {
        JPanel panel = new JPanel();
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                NbBundle.getMessage(UIBundle.class, headerKey),
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
        chooser.setDialogTitle(NbBundle.getMessage(UIBundle.class, "TITLE_SelectExecutable"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            userEditedPath = true;
            pathCombo.getEditor().setItem(chooser.getSelectedFile().getAbsolutePath());
            onPathChanged();
        }
    }

    private void iconBrowseButtonActionPerformed() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images (*.png)", "png"));
        chooser.setDialogTitle(NbBundle.getMessage(UIBundle.class, "TITLE_SelectIcon"));
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

        // Discover known binaries on PATH. Native probing only stats the filesystem; the
        // WSL probe spawns wsl.exe per name (up to 2 s each) and so runs off the EDT —
        // load() is called on the EDT by the Options SPI.
        boolean useWsl = BinaryResolver.isWslAvailable();
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
        discoverWslBinariesAsync(useWsl);

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

        // Stored id wins when the path cannot explain it (custom binary or container
        // launcher); otherwise the state is Auto-detect with the path as the source.
        HarnessSelection.Selection storedChoice = HarnessSelection.resolve(
                NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get(PreferenceKeys.ACP_HARNESS_ID, null),
                BinaryResolver.binaryNameFromPath(getExecutablePath()));
        if (storedChoice.auto()) {
            harnessCombo.setSelectedIndex(0);
        } else {
            harnessCombo.setSelectedItem(storedChoice);
        }
        refreshHarnessHint();

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
        updateContextActionsSummary();
        fsPolicyCombo.setSelectedItem(PluginSettings.getAcpFsPolicy());
        updateFsPolicyTooltip();
        runCommandAllowlist = PluginSettings.getRunCommandAllowlist();
        runCommandDenylist = PluginSettings.getRunCommandDenylist();
        updateRunCommandTooltip();
        projectSwitchRestartCheckbox.setSelected(PluginSettings.isProjectSwitchRestartConfigured());
        markdownProjectCheckbox.setSelected(PluginSettings.isMarkdownProjectEnabled());
        mcpServerCheckbox.setSelected(PluginSettings.isMcpServerEnabled());
        mcpPortSpinner.setValue(PluginSettings.getMcpServerPort());
        useWslCheckbox.setSelected(PluginSettings.isUseWsl());
        updateGeminiModelHint();
        // Reopening Options (including after Cancel) must still show what is outstanding.
        refreshPendingRestartBar();
    }

    /**
     * Probes WSL for catalog binaries off the EDT. Each probe spawns {@code wsl.exe}, which
     * can block for the 2 s timeout, and {@link #load()} runs on the EDT. Results are appended
     * to the combo when they arrive; a selection already made by the user or by {@code load()}
     * is left alone.
     */
    private void discoverWslBinariesAsync(boolean useWsl) {
        int generation = ++wslDiscoveryGeneration;
        if (!useWsl) {
            return;
        }
        String[] candidates = BinaryResolver.linuxHarnessNames();
        DISCOVERY_RP.post(() -> {
            List<String> found = new ArrayList<>();
            for (String name : candidates) {
                if (generation != wslDiscoveryGeneration) {
                    return; // superseded by a newer load() — stop spawning wsl.exe
                }
                String path = BinaryResolver.findOnWslPath(name);
                if (path != null) {
                    found.add(path);
                }
            }
            if (!found.isEmpty()) {
                SwingUtilities.invokeLater(() -> applyWslCandidates(generation, found));
            }
        });
    }

    /** Appends asynchronously probed WSL binaries to the path combo, unless the state moved on. */
    private void applyWslCandidates(int generation, List<String> found) {
        if (generation != wslDiscoveryGeneration) {
            return; // a newer load() superseded this probe
        }
        Object previousSelection = pathCombo.getSelectedItem();
        Object editorItem = pathCombo.getEditor().getItem();
        String previousEditorText = editorItem != null ? editorItem.toString() : "";
        boolean selectionMade = userEditedPath || !isBlank(getExecutablePath());
        boolean added = false;
        for (String path : found) {
            if (pathLabels.containsKey(path)) {
                continue; // native + WSL can resolve to the same path
            }
            pathLabels.put(path, path + " (WSL)");
            pathCombo.addItem(path);
            added = true;
        }
        if (!added) {
            return;
        }
        if (selectionMade) {
            // DefaultComboBoxModel auto-selects the first item added to an empty model, which
            // replaces text the user is still typing: put the pre-probe value back.
            pathCombo.setSelectedItem(previousSelection != null ? previousSelection : previousEditorText);
            return;
        }
        // The first added item is now the selection; refresh the hint that depends on the path.
        updateGeminiModelHint();
        refreshHarnessHint();
    }

    /** Opens the allow/deny editor; settings stay local until Options is applied. */
    private void editRunCommandPolicy() {
        RunCommandPolicyDialog.show(this, runCommandAllowlist, runCommandDenylist).ifPresent(result -> {
            runCommandAllowlist = result.allowlist();
            runCommandDenylist = result.denylist();
            updateRunCommandTooltip();
            controller.changed();
        });
    }

    /** Summarises the current run_command policy in the button tooltip. */
    private void updateRunCommandTooltip() {
        String summary = NbBundle.getMessage(UIBundle.class, "TT_RunCommandPolicy")
                + "\nAllowed: " + (runCommandAllowlist.isBlank() ? "(any)" : runCommandAllowlist)
                + "\nDenied: " + (runCommandDenylist.isBlank() ? "(none)" : runCommandDenylist);
        runCommandPolicyButton.setToolTipText(wrapTooltip(summary));
    }

    /** Opens the editor-context action toggles; values apply when Options is applied. */
    private void editContextActions() {
        List<ContextActionsDialog.Toggle> toggles = contextActionToggles();
        ContextActionsDialog.show(this, toggles).ifPresent(selected -> {
            for (int i = 0; i < toggles.size(); i++) {
                toggles.get(i).setter().accept(selected[i]);
            }
            updateContextActionsSummary();
            controller.changed();
        });
    }

    /** The editor-context toggles edited in the Configure dialog. */
    private List<ContextActionsDialog.Toggle> contextActionToggles() {
        return List.of(
                toggle("LBL_ActionSortLines", "TT_SortLines", sortLinesCheckbox),
                toggle("LBL_ActionCompactJson", "TT_CompactJson", compactJsonCheckbox),
                toggle("LBL_ActionSearchWeb", "TT_SearchWeb", searchWebCheckbox),
                toggle("LBL_ActionShowAnnotations", "TT_ShowAnnotations", showAnnotationsCheckbox),
                toggle("LBL_ActionViewFileHistory", "TT_ViewFileHistory", viewFileHistoryCheckbox),
                toggle("LBL_ActionStashDiff", "TT_StashDiff", stashDiffCheckbox),
                toggle("LBL_ActionQuickJump", "TT_QuickJump", quickJumpCheckbox),
                toggle("LBL_ActionCopyAllItems", "TT_CopyAllItems", copyAllItemsCheckbox));
    }

    private static ContextActionsDialog.Toggle toggle(String labelKey, String tooltipKey, JCheckBox box) {
        return new ContextActionsDialog.Toggle(
                NbBundle.getMessage(UIBundle.class, labelKey),
                tooltip(tooltipKey),
                box::isSelected,
                box::setSelected);
    }

    /** Refreshes "N of M enabled" from the current toggle states. */
    private void updateContextActionsSummary() {
        List<ContextActionsDialog.Toggle> toggles = contextActionToggles();
        long enabled = toggles.stream().filter(t -> t.selected().getAsBoolean()).count();
        contextActionsSummary.setText(NbBundle.getMessage(UIBundle.class, "LBL_ContextActions",
                enabled, toggles.size()));
    }

    /** Short label for one file-access policy option. */
    private static String fsPolicyLabel(AcpFsPolicy policy) {
        String key = switch (policy) {
            case ALLOW -> "LBL_FsAllowShort";
            case READ_ANY_WRITE_PROJECT -> "LBL_FsReadExternalShort";
            case READ_ONLY -> "LBL_FsReadOnlyShort";
        };
        return NbBundle.getMessage(UIBundle.class, key);
    }

    /** Explains the selected policy in the combo's tooltip. */
    private void updateFsPolicyTooltip() {
        Object selected = fsPolicyCombo.getSelectedItem();
        if (selected instanceof AcpFsPolicy policy) {
            fsPolicyCombo.setToolTipText(tooltip("TT_Fs" + switch (policy) {
                case ALLOW -> "Allow";
                case READ_ANY_WRITE_PROJECT -> "ReadExternal";
                case READ_ONLY -> "ReadOnly";
            }));
        }
    }

    /** Shows a reminder to change --model when the selected binary is Gemini. */
    private void updateGeminiModelHint() {
        String binName = BinaryResolver.binaryNameFromPath(getExecutablePath());
        HarnessCatalog.Harness h = binName != null ? HarnessCatalog.byBinaryName(binName) : null;
        if (h != null && "gemini".equals(h.id())) {
            argsHintLabel.setText(NbBundle.getMessage(UIBundle.class, "MSG_GeminiDefaultModel"));
            argsHintLabel.setVisible(true);
        } else {
            argsHintLabel.setText("");
            argsHintLabel.setVisible(false);
        }
    }

    /** Harness detected from the current path, or {@link HarnessCatalog#UNKNOWN}. */
    private HarnessCatalog.Harness detectedHarness() {
        return HarnessSelection.detect(BinaryResolver.binaryNameFromPath(getExecutablePath()));
    }

    /** Renders the Auto item with the current detection result inline. */
    private class HarnessChoiceRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setToolTipText(null);
            if (value instanceof HarnessSelection.Selection selection) {
                setText(harnessChoiceLabel(selection));
                HarnessCatalog.Harness harness = selection.harness();
                if (harness.hasLimitedSupport() && !harness.limitations().isBlank()) {
                    setToolTipText(harness.limitations());
                }
            }
            return this;
        }
    }

    /** Label for one combo entry; Auto names the detected harness when there is one. */
    private String harnessChoiceLabel(HarnessSelection.Selection selection) {
        if (!selection.auto()) {
            HarnessCatalog.Harness harness = selection.harness();
            return harness.hasLimitedSupport()
                    ? NbBundle.getMessage(UIBundle.class, "OPT_Harness_Limited", harness.displayName())
                    : harness.displayName();
        }
        HarnessCatalog.Harness detected = detectedHarness();
        return detected == HarnessCatalog.UNKNOWN
                ? NbBundle.getMessage(UIBundle.class, "OPT_Harness_AutoDetectUnknown")
                : NbBundle.getMessage(UIBundle.class, "OPT_Harness_AutoDetect", detected.displayName());
    }

    /**
     * Rewrites the hint for the current path and selection. An explicit choice is never
     * replaced here; a mismatch is surfaced as a one-click switch instead.
     */
    private void refreshHarnessHint() {
        if (harnessCombo == null) {
            return;
        }
        String binaryName = BinaryResolver.binaryNameFromPath(getExecutablePath());
        HarnessCatalog.Harness detected = HarnessSelection.detect(binaryName);
        HarnessSelection.Selection selection =
                (HarnessSelection.Selection) harnessCombo.getSelectedItem();
        boolean auto = selection == null || selection.auto();
        boolean conflict = !auto && detected != HarnessCatalog.UNKNOWN && detected != selection.harness();
        if (conflict) {
            harnessHintLabel.setText(NbBundle.getMessage(UIBundle.class, "HLP_HarnessConflict",
                    detected.displayName(), selection.harness().displayName()));
            harnessUseDetectedButton.setText(NbBundle.getMessage(UIBundle.class,
                    "BTN_HarnessUseDetected", detected.displayName()));
        } else if (!auto) {
            harnessHintLabel.setText(NbBundle.getMessage(UIBundle.class, "HLP_HarnessManual"));
        } else if (detected != HarnessCatalog.UNKNOWN) {
            harnessHintLabel.setText(NbBundle.getMessage(UIBundle.class, "HLP_HarnessDetected"));
        } else if (HarnessSelection.launcherKind(binaryName) == HarnessSelection.Launcher.CONTAINER) {
            harnessHintLabel.setText(NbBundle.getMessage(UIBundle.class, "HLP_HarnessContainer"));
        } else {
            harnessHintLabel.setText(NbBundle.getMessage(UIBundle.class, "HLP_HarnessNotDetected"));
        }
        harnessUseDetectedButton.setVisible(conflict);
        harnessHintRow.revalidate();
        harnessCombo.repaint();
    }

    /** Conflict shortcut: trust the binary name and drop back to Auto-detect. */
    private void useDetectedHarness() {
        harnessCombo.setSelectedIndex(0);
        refreshHarnessHint();
        controller.changed();
    }

    /** Path field changed: re-run detection without touching an explicit harness choice. */
    private void onPathChanged() {
        refreshHarnessHint();
        controller.changed();
    }

    /** Returns the currently selected/entered executable path from the combo. */
    private String getExecutablePath() {
        Object item = pathCombo.getSelectedItem();
        return item != null ? item.toString().trim() : "";
    }

    /**
     * Horizontal offset from a checkbox's left edge to the first character of its text.
     * Some LAFs leave {@link AbstractButton#getIcon()} null and paint the glyph
     * from {@code CheckBox.icon}, so both sources are consulted before falling back to the
     * component insets alone.
     */
    static int checkboxTextInset(AbstractButton button) {
        Icon icon = button.getIcon();
        if (icon == null && UIManager.get("CheckBox.icon") instanceof Icon lafIcon) {
            icon = lafIcon;
        }
        return button.getInsets().left
                + (icon != null ? icon.getIconWidth() : 0)
                + button.getIconTextGap();
    }

    /**
     * Splits a hint into the two lines it is rendered on: at its sentence break when it has
     * one, otherwise at the space nearest the middle. One line is returned when the text
     * has no usable break.
     */
    static List<String> splitHint(String text) {
        int sentence = text.indexOf(". ");
        int cut = sentence >= 0 ? sentence + 1 : text.indexOf(' ', text.length() / 2);
        if (cut <= 0 || cut >= text.length() - 1) {
            return List.of(text);
        }
        return List.of(text.substring(0, cut).trim(), text.substring(cut).trim());
    }

    /**
     * Permanent footnote marker for an option whose value the running IDE only reads at
     * startup (or at session creation). One character, appended to the control's own text
     * so it is part of the accessible name and mirrors under RTL; {@link #createScopeLegend}
     * explains the markers once at the top of the panel.
     */
    private static String scopeSuffix(PluginSettings.Effect effect) {
        return switch (effect) {
            case IDE_RESTART -> NbBundle.getMessage(UIBundle.class, "SCOPE_Restart");
            case NEW_SESSION -> NbBundle.getMessage(UIBundle.class, "SCOPE_NewSession");
            case SESSION_RELOAD -> NbBundle.getMessage(UIBundle.class, "SCOPE_SessionReload");
            case IMMEDIATE -> "";
        };
    }

    /** One muted line explaining the footnote markers appended by {@link #scopedLabel}. */
    private static JLabel createScopeLegend() {
        JLabel legend = new JLabel(NbBundle.getMessage(UIBundle.class, "SCOPE_Legend"));
        legend.setForeground(UIManager.getColor("Label.disabledForeground"));
        legend.setFont(legend.getFont().deriveFont(Font.PLAIN, legend.getFont().getSize() - 1f));
        legend.setAlignmentX(Component.CENTER_ALIGNMENT);
        return legend;
    }

    /** Bundle label plus its permanent scope marker. */
    private static String scopedLabel(String key, PluginSettings.Effect effect) {
        return NbBundle.getMessage(UIBundle.class, key) + scopeSuffix(effect);
    }

    /** Bundle-backed tooltip, wrapped so long hints stay readable. */
    private static String tooltip(String key) {
        return wrapTooltip(NbBundle.getMessage(UIBundle.class, key));
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
        pendingRestartBody = new JLabel(NbBundle.getMessage(UIBundle.class, "RST_Pending_Body"));
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
            return NbBundle.getMessage(UIBundle.class, "RST_Pending_Single", first);
        }
        return NbBundle.getMessage(UIBundle.class, "RST_Pending_Multiple", first, keys.size() - 1);
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
        return bundleKey == null ? key : NbBundle.getMessage(UIBundle.class, bundleKey);
    }

    public void store() {
        String pathToSave = getExecutablePath();
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(PreferenceKeys.ACP_EXECUTABLE_PATH, pathToSave);
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(PreferenceKeys.PROCESS_ARGUMENTS, argsField.getText());
        // Persist the chosen harness, not just the one the file name implies: a custom
        // binary or a container launcher (docker/podman) cannot reveal the harness.
        String binName = BinaryResolver.binaryNameFromPath(pathToSave);
        HarnessSelection.Selection choice = (HarnessSelection.Selection) harnessCombo.getSelectedItem();
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(
                PreferenceKeys.ACP_HARNESS_ID, HarnessSelection.harnessId(choice, binName));
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(
                PreferenceKeys.ACP_HARNESS_DISPLAY_NAME, HarnessSelection.displayName(choice, binName));
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
        PluginSettings.setAcpFsPolicy((AcpFsPolicy) fsPolicyCombo.getSelectedItem());
        PluginSettings.setRunCommandAllowlist(runCommandAllowlist);
        PluginSettings.setRunCommandDenylist(runCommandDenylist);
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
        String title = NbBundle.getMessage(UIBundle.class, "LBL_PreambleTooLong");
        String message = NbBundle.getMessage(UIBundle.class, "MSG_PreambleTooLong",
                length, PluginSettings.MAX_PREAMBLE_LENGTH);
        LOG.log(Level.WARNING, "Session preamble not saved: {0} characters", length);
        SwingUtilities.invokeLater(() -> NotificationDisplayer.getDefault().notify(
                title, NotificationDisplayer.Priority.LOW.getIcon(), message, null,
                NotificationDisplayer.Priority.LOW));
    }

    public boolean valid() {
        if (!userEditedPath) {
            setPathError("");
            return true;
        }
        String path = getExecutablePath();
        if (isBlank(path)) {
            setPathError(NbBundle.getMessage(UIBundle.class, "ERR_EmptyPath"));
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
                setPathError(NbBundle.getMessage(UIBundle.class, "ERR_WslNotAvailable"));
                pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
                return false;
            }
            setPathError("");
            return true;
        }
        File f = new File(path.trim());
        if (!f.exists()) {
            setPathError(NbBundle.getMessage(UIBundle.class, "ERR_PathNotFound"));
            pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            return false;
        }
        if (!f.isFile()) {
            setPathError(NbBundle.getMessage(UIBundle.class, "ERR_PathNotFile"));
            pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            return false;
        }
        if (!f.canExecute()) {
            setPathError(NbBundle.getMessage(UIBundle.class, "ERR_PathNotExecutable"));
            pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            return false;
        }
        if (isWindows) {
            String lower = path.toLowerCase();
            if (!lower.endsWith(".exe") && !lower.endsWith(".cmd") && !lower.endsWith(".bat")) {
                setPathError(NbBundle.getMessage(UIBundle.class, "ERR_MissingExeExtension"));
                pathErrorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
                return false;
            }
        }
        setPathError("");
        return true;
    }

    /** An empty message hides its row, so the grid keeps one gap between every field row. */
    private void setPathError(String text) {
        pathErrorLabel.setText(text);
        pathErrorLabel.setVisible(!text.isEmpty());
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
