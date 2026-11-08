package github.anandb.netbeans.ui.options;

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
import org.openide.util.RequestProcessor;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Box;
import javax.swing.border.TitledBorder;
import javax.swing.border.EmptyBorder;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.Scrollable;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.isBlank;
import github.anandb.netbeans.support.PreferenceKeys;
import org.openide.awt.NotificationDisplayer;
import org.openide.awt.ColorComboBox;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import github.anandb.netbeans.ui.rendering.ColorTheme;
import github.anandb.netbeans.ui.rendering.IconPreviewManager;
import github.anandb.netbeans.ui.platform.SessionService;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.components.UIUtils;
import github.anandb.netbeans.model.Harness;

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
    private JCheckBox diffIdeFontCheckbox;
    private JCheckBox mcpServerCheckbox;
    private JSpinner mcpPortSpinner;
    private JCheckBox useWslCheckbox;
    private JCheckBox projectSwitchRestartCheckbox;
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
    private JPanel iconPreviewBox;
    private ColorComboBox userBubbleLightCombo;
    private ColorComboBox userBubbleDarkCombo;
    private ColorComboBox assistantBubbleLightCombo;
    private ColorComboBox assistantBubbleDarkCombo;
    private JComboBox<String> chatFontFamilyCombo;
    private JPanel pendingRestartBar;
    private JPanel detailCards;
    private JLabel detailHeader;
    private JScrollPane detailScroll;
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

    private int savedTooltipDismissDelay = -1;

    /**
     * Swing's tooltip timeout is process-wide, so the panel doubles it while it is on screen
     * and restores the previous value when the Options dialog closes.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        if (savedTooltipDismissDelay < 0) {
            ToolTipManager tips = ToolTipManager.sharedInstance();
            savedTooltipDismissDelay = tips.getDismissDelay();
            tips.setDismissDelay((int) Math.min(Integer.MAX_VALUE, savedTooltipDismissDelay * 2L));
        }
    }

    @Override
    public void removeNotify() {
        if (savedTooltipDismissDelay >= 0) {
            ToolTipManager.sharedInstance().setDismissDelay(savedTooltipDismissDelay);
            savedTooltipDismissDelay = -1;
        }
        super.removeNotify();
    }

    private void initComponents() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        createPendingRestartBar();
        add(createScopeLegend());
        add(Box.createVerticalStrut(2));

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
        diffIdeFontCheckbox = new JCheckBox();
        mcpServerCheckbox = new JCheckBox();
        preambleEnabledCheckbox = new JCheckBox();
        projectSwitchRestartCheckbox = new JCheckBox();
        iconLabel = new JLabel();
        iconPathField = new JTextField(18);
        iconBrowseButton = new JButton();
        iconPreviewLabel = new JLabel();

        iconPreviewManager = new IconPreviewManager(iconPreviewLabel, iconPathField, PREVIEW_SIZE,
                controller::changed);

        // --- Assistant ---
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
                Harness h = binName != null ? HarnessCatalog.byBinaryName(binName) : null;
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
        for (Harness harness : HarnessCatalog.sortedByName()) {
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

        // --- System ---
        JPanel systemPanel = createSectionPanel("LBL_GeneralHeader");
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

        // Experimental options live on their own page: no border nested in the General page.
        JPanel experimentalPanel = createSectionPanel("LBL_ExperimentalHeader");
        experimentalPanel.setLayout(new GridBagLayout());

        projectSwitchRestartCheckbox.setText(
                scopedLabel("LBL_ProjectSwitchRestart", PluginSettings.Effect.IDE_RESTART));
        // The hint is part of the tooltip: the checkbox no longer prints a paragraph under it.
        projectSwitchRestartCheckbox.setToolTipText(wrapTooltip(
                NbBundle.getMessage(UIBundle.class, "TT_ProjectSwitchRestart")
                        + "\n" + NbBundle.getMessage(UIBundle.class, "HLP_ProjectSwitchRestart")));
        projectSwitchRestartCheckbox.addActionListener(evt -> controller.changed());
        experimentalPanel.add(projectSwitchRestartCheckbox, UIUtils.createGbc(0, 0, 1.0, 0,
                GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST, new Insets(0, 12, 5, 12)));

        autoBackupChangesCheckbox.setText(NbBundle.getMessage(UIBundle.class, "LBL_AutoBackupChanges"));
        autoBackupChangesCheckbox.setToolTipText(tooltip("TT_AutoBackupChanges"));
        autoBackupChangesCheckbox.addActionListener(evt -> controller.changed());
        experimentalPanel.add(autoBackupChangesCheckbox, UIUtils.createGbc(0, 1, 1.0, 0,
                GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST, new Insets(0, 12, 5, 12)));

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

        // --- Chat Behavior ---
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

        // --- Session Limits ---
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

        // --- IDE Extensions ---
        JPanel actionsPanel = createSectionPanel("LBL_FeaturesHeader");
        actionsPanel.setLayout(new GridBagLayout());

        markdownProjectCheckbox.setText(NbBundle.getMessage(UIBundle.class, "LBL_EnableMarkdownProject"));
        markdownProjectCheckbox.setToolTipText(tooltip("TT_EnableMarkdownProject"));
        markdownProjectCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(markdownProjectCheckbox, UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        taskRepositoryCheckbox.setText(NbBundle.getMessage(UIBundle.class, "LBL_EnableTaskRepository"));
        taskRepositoryCheckbox.setToolTipText(tooltip("TT_EnableTaskRepository"));
        taskRepositoryCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(taskRepositoryCheckbox, UIUtils.createGbc(0, 1, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        miniAssistantCheckbox.setText(NbBundle.getMessage(UIBundle.class, "LBL_EnableMiniAssistant"));
        miniAssistantCheckbox.setToolTipText(tooltip("TT_EnableMiniAssistant"));
        miniAssistantCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(miniAssistantCheckbox, UIUtils.createGbc(0, 2, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        diffIdeFontCheckbox.setText(scopedLabel("LBL_DiffIdeFont", PluginSettings.Effect.IDE_RESTART));
        diffIdeFontCheckbox.setToolTipText(tooltip("TT_DiffIdeFont"));
        diffIdeFontCheckbox.addActionListener(evt -> controller.changed());
        actionsPanel.add(diffIdeFontCheckbox, UIUtils.createGbc(0, 3, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));

        JPanel contextActionsPanel = buildContextActionsPage();

        // --- Appearance ---
        JPanel appearancePanel = createSectionPanel("LBL_AppearanceHeader");
        // Two independent columns: the form keeps one row rhythm, and the preview box no
        // longer stretches the first rows just because it spans them.
        appearancePanel.setLayout(new BorderLayout(8, 0));
        JPanel appearanceForm = new JPanel(new GridBagLayout());
        appearanceForm.setOpaque(false);
        appearancePanel.add(appearanceForm, BorderLayout.CENTER);
        row = 0;

        iconLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_UserIcon"));
        iconLabel.setToolTipText(tooltip("TT_UserIcon"));
        appearanceForm.add(iconLabel, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));

        iconPathField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) {
                iconPreviewManager.updatePreview(iconPathField.getText());
                controller.changed();
            }
        });
        iconPathField.setToolTipText(tooltip("TT_UserIcon"));
        appearanceForm.add(iconPathField, UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                             GridBagConstraints.EAST, new Insets(0, 0, 5, 5)));

        iconBrowseButton.setText(NbBundle.getMessage(UIBundle.class, "BTN_Browse"));
        iconBrowseButton.setToolTipText(tooltip("TT_UserIcon"));
        iconBrowseButton.addActionListener(evt -> iconBrowseButtonActionPerformed());
        appearanceForm.add(iconBrowseButton, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.EAST, new Insets(0, 0, 5, 5)));

        // The mock groups the avatar in its own titled box, so the preview reads as a picture
        // rather than a floating label next to the size controls. The box must be at least as
        // wide as its title (a TitledBorder clips at the box width) and the avatar keeps a
        // square of its own inside it, so the grid centres the label at its preferred size.
        iconPreviewBox = createSectionPanel("LBL_AvatarPreviewHeader");
        iconPreviewBox.setLayout(new GridBagLayout());
        iconPreviewBox.add(iconPreviewLabel);
        Insets boxInsets = iconPreviewBox.getInsets();
        Font titleFont = iconPreviewBox.getFont().deriveFont(Font.BOLD);
        int titleWidth = iconPreviewBox.getFontMetrics(titleFont)
                .stringWidth(NbBundle.getMessage(UIBundle.class, "LBL_AvatarPreviewHeader")) + 12;
        iconPreviewBox.setPreferredSize(new Dimension(
                Math.max(PREVIEW_SIZE + boxInsets.left + boxInsets.right, titleWidth),
                PREVIEW_SIZE + boxInsets.top + boxInsets.bottom));

        JPanel previewColumn = new JPanel(new BorderLayout());
        previewColumn.setOpaque(false);
        previewColumn.add(iconPreviewBox, BorderLayout.NORTH);
        appearancePanel.add(previewColumn, BorderLayout.EAST);
        row++;
        JLabel toolbarIconLabel = new JLabel(scopedLabel("LBL_ToolbarIconSize",
                PluginSettings.Effect.IDE_RESTART));
        toolbarIconLabel.setToolTipText(tooltip("TT_ToolbarIconSize"));
        appearanceForm.add(toolbarIconLabel, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));

        toolbarIconCombo = new JComboBox<>(new String[]{"16", "24", "28", "32", "48"});
        toolbarIconCombo.setToolTipText(tooltip("TT_ToolbarIconSize"));
        toolbarIconCombo.setPrototypeDisplayValue("100");
        toolbarIconCombo.addActionListener(evt -> controller.changed());
        appearanceForm.add(toolbarIconCombo, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 5)));

        row++;
        JLabel chatFontLabel = new JLabel(scopedLabel("LBL_ChatFontSize", PluginSettings.Effect.SESSION_RELOAD));
        chatFontLabel.setToolTipText(tooltip("TT_ChatFontSize"));
        appearanceForm.add(chatFontLabel, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));

        chatFontCombo = new JComboBox<>(new String[]{"Inherited", "10", "11", "12", "13", "14", "16", "18", "20", "22", "24"});
        chatFontCombo.setToolTipText(tooltip("TT_ChatFontSize"));
        // "Inherited" is the widest value and the stored default, so it sets the width.
        chatFontCombo.setPrototypeDisplayValue("Inherited");
        chatFontCombo.addActionListener(evt -> controller.changed());
        appearanceForm.add(chatFontCombo, UIUtils.createGbc(2, row, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.EAST, new Insets(0, 0, 5, 5)));

        userBubbleLightCombo = colorCombo("TT_UserBubbleLight",
                ColorTheme.configuredBubbleColor(true, false));
        userBubbleDarkCombo = colorCombo("TT_UserBubbleDark",
                ColorTheme.configuredBubbleColor(true, true));
        addColorRow(appearanceForm, ++row, "LBL_UserBubbleBackground",
                userBubbleLightCombo, userBubbleDarkCombo);
        assistantBubbleLightCombo = colorCombo("TT_AssistantBubbleLight",
                ColorTheme.configuredBubbleColor(false, false));
        assistantBubbleDarkCombo = colorCombo("TT_AssistantBubbleDark",
                ColorTheme.configuredBubbleColor(false, true));
        addColorRow(appearanceForm, ++row, "LBL_AssistantBubbleBackground",
                assistantBubbleLightCombo, assistantBubbleDarkCombo);

        JLabel chatFontFamilyLabel = new JLabel(scopedLabel("LBL_ChatFontFamily",
                PluginSettings.Effect.SESSION_RELOAD));
        chatFontFamilyLabel.setToolTipText(tooltip("TT_ChatFontFamily"));
        appearanceForm.add(chatFontFamilyLabel, UIUtils.createGbc(0, ++row, 0.0, 0,
                GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));
        chatFontFamilyCombo = new JComboBox<>(fontFamilyItems());
        chatFontFamilyCombo.setToolTipText(tooltip("TT_ChatFontFamily"));
        chatFontFamilyCombo.setPrototypeDisplayValue("SansSerif");
        chatFontFamilyCombo.addActionListener(evt -> controller.changed());
        GridBagConstraints familyGbc = UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 0, 5, 5));
        familyGbc.gridwidth = 2;
        appearanceForm.add(chatFontFamilyCombo, familyGbc);

        add(createNavigation(servicePanel, behaviorPanel, appearancePanel,
                systemPanel, experimentalPanel, limitsPanel, actionsPanel, contextActionsPanel));
    }

    /** One bubble type: label, then the light and dark colour pickers side by side. */
    private void addColorRow(JPanel panel, int row, String labelKey,
            ColorComboBox light, ColorComboBox dark) {
        JLabel label = new JLabel(scopedLabel(labelKey, PluginSettings.Effect.SESSION_RELOAD));
        panel.add(label, UIUtils.createGbc(0, row, 0.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 5)));

        JPanel pickers = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pickers.setOpaque(false);
        pickers.add(caption("LBL_Light"));
        pickers.add(light);
        pickers.add(caption("LBL_Dark"));
        pickers.add(dark);
        GridBagConstraints gbc = UIUtils.createGbc(1, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 0, 5, 5));
        gbc.gridwidth = 2;
        panel.add(pickers, gbc);
    }

    private static JLabel caption(String key) {
        return new JLabel(NbBundle.getMessage(UIBundle.class, key));
    }

    /**
     * NetBeans' own colour picker ({@link ColorComboBox}): a swatch, the colour's value and a
     * Custom entry that opens {@link javax.swing.JColorChooser}. The first entry is the default
     * (theme) colour, which stores no override.
     */
    private ColorComboBox colorCombo(String tooltipKey, Color themeColor) {
        ColorComboBox combo = new ColorComboBox(new Color[]{themeColor},
                new String[]{NbBundle.getMessage(UIBundle.class, "LBL_DefaultEntry")}, true);
        combo.setToolTipText(wrapTooltip(NbBundle.getMessage(UIBundle.class, tooltipKey)
                + "\n" + NbBundle.getMessage(UIBundle.class, "TT_ColorCombo")));
        combo.addActionListener(evt -> controller.changed());
        combo.setPreferredSize(new Dimension(160, combo.getPreferredSize().height));
        return combo;
    }

    /** Selects the stored colour, or the default entry when no override is stored. */
    private static void selectColor(ColorComboBox combo, String value, Color themeColor) {
        if (value == null || value.isBlank()) {
            combo.setSelectedColor(themeColor);
            return;
        }
        try {
            combo.setSelectedColor(Color.decode(value.trim()));
        } catch (NumberFormatException ignored) {
            // an unreadable stored value falls back to the theme colour
            combo.setSelectedColor(themeColor);
        }
    }

    /** The chosen colour as {@code #rrggbb}, or empty when the default entry is selected. */
    private static String selectedColorValue(ColorComboBox combo, Color themeColor) {
        Color selected = combo.getSelectedColor();
        if (selected == null || selected.equals(themeColor)) {
            return "";
        }
        return toHex(selected);
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** Installed font families, with "Inherited" first so the empty value is visible. */
    private static String[] fontFamilyItems() {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames(Locale.ROOT);
        Arrays.sort(families);
        String[] items = new String[families.length + 1];
        items[0] = NbBundle.getMessage(UIBundle.class, "LBL_FontInherited");
        System.arraycopy(families, 0, items, 1, families.length);
        return items;
    }

    /** Selects the stored family, keeping an uninstalled one visible instead of resetting it. */
    private void applyFontFamily(String family) {
        if (family == null || family.isBlank()) {
            chatFontFamilyCombo.setSelectedIndex(0);
            return;
        }
        for (int i = 0; i < chatFontFamilyCombo.getItemCount(); i++) {
            if (family.equals(chatFontFamilyCombo.getItemAt(i))) {
                chatFontFamilyCombo.setSelectedIndex(i);
                return;
            }
        }
        chatFontFamilyCombo.addItem(family);
        chatFontFamilyCombo.setSelectedItem(family);
    }

    /** The chosen family, or an empty string for the "Inherited" row. */
    private String selectedFontFamily() {
        Object item = chatFontFamilyCombo.getSelectedItem();
        if (item == null || chatFontFamilyCombo.getSelectedIndex() == 0) {
            return "";
        }
        return item.toString();
    }

    /** Persists the five appearance overrides; returns true when any value changed. */
    private boolean writeAppearanceSettings() {
        Color userLightTheme = ColorTheme.configuredBubbleColor(true, false);
        Color userDarkTheme = ColorTheme.configuredBubbleColor(true, true);
        Color assistantLightTheme = ColorTheme.configuredBubbleColor(false, false);
        Color assistantDarkTheme = ColorTheme.configuredBubbleColor(false, true);
        String userLight = selectedColorValue(userBubbleLightCombo, userLightTheme);
        String userDark = selectedColorValue(userBubbleDarkCombo, userDarkTheme);
        String assistantLight = selectedColorValue(assistantBubbleLightCombo, assistantLightTheme);
        String assistantDark = selectedColorValue(assistantBubbleDarkCombo, assistantDarkTheme);
        boolean changed = !userLight.equals(PluginSettings.getUserBubbleBackgroundLight())
                || !userDark.equals(PluginSettings.getUserBubbleBackgroundDark())
                || !assistantLight.equals(PluginSettings.getAssistantBubbleBackgroundLight())
                || !assistantDark.equals(PluginSettings.getAssistantBubbleBackgroundDark())
                || !selectedFontFamily().equals(PluginSettings.getChatFontFamily());
        PluginSettings.setUserBubbleBackgroundLight(userLight);
        PluginSettings.setUserBubbleBackgroundDark(userDark);
        PluginSettings.setAssistantBubbleBackgroundLight(assistantLight);
        PluginSettings.setAssistantBubbleBackgroundDark(assistantDark);
        PluginSettings.setChatFontFamily(selectedFontFamily());
        if (changed) {
            ThemeManager.reloadTheme();
            ThemeManager.clearFontCache();
            UIUtils.clearFontStackCache();
        }
        return changed;
    }

    /** The editor context-menu toggles, shown inline instead of behind a Configure dialog. */
    private JPanel buildContextActionsPage() {
        JPanel panel = createSectionPanel("LBL_ContextActionsHeader");
        panel.setLayout(new GridBagLayout());
        int row = 0;
        addActionToggle(panel, row++, sortLinesCheckbox, "LBL_ActionSortLines", "TT_SortLines");
        addActionToggle(panel, row++, compactJsonCheckbox, "LBL_ActionCompactJson", "TT_CompactJson");
        addActionToggle(panel, row++, searchWebCheckbox, "LBL_ActionSearchWeb", "TT_SearchWeb");
        addActionToggle(panel, row++, showAnnotationsCheckbox, "LBL_ActionShowAnnotations", "TT_ShowAnnotations");
        addActionToggle(panel, row++, viewFileHistoryCheckbox, "LBL_ActionViewFileHistory", "TT_ViewFileHistory");
        addActionToggle(panel, row++, stashDiffCheckbox, "LBL_ActionStashDiff", "TT_StashDiff");
        addActionToggle(panel, row++, quickJumpCheckbox, "LBL_ActionQuickJump", "TT_QuickJump");
        addActionToggle(panel, row, copyAllItemsCheckbox, "LBL_ActionCopyAllItems", "TT_CopyAllItems");
        return panel;
    }

    private void addActionToggle(JPanel panel, int row, JCheckBox box, String labelKey, String tooltipKey) {
        box.setText(NbBundle.getMessage(UIBundle.class, labelKey));
        box.setToolTipText(tooltip(tooltipKey));
        box.addActionListener(evt -> controller.changed());
        panel.add(box, UIUtils.createGbc(0, row, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));
    }

    /**
     * Trailing space inside the category tree, past its longest row. The tree sizes itself
     * from that preferred width, so a wider IDE font still fits and no horizontal scroll
     * bar is ever needed.
     */
    private static final int NAV_TRAILING_PADDING = 24;
    /** Room a settings page gets before it scrolls; the dialog can still be resized. */
    private static final int DETAIL_PREF_HEIGHT = 480;

    /**
     * Builds the master/detail body: a category tree on the left and one settings page at
     * a time on the right. The tree keeps a fixed width so the forms keep theirs.
     */
    private JPanel createNavigation(JPanel service, JPanel behavior, JPanel appearance,
            JPanel system, JPanel experimental, JPanel limits, JPanel actions, JPanel contextActions) {
        detailCards = new ScrollableCards();
        addCard("service", service);
        addCard("behavior", behavior);
        addCard("appearance", appearance);
        addCard("general", system);
        addCard("experimental", experimental);
        addCard("limits", limits);
        addCard("extensions", actions);
        addCard("contextActions", contextActions);

        JTree tree = new JTree(buildNavModel());
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(1);
        tree.setCellRenderer(new NavRenderer());
        tree.setBorder(new EmptyBorder(4, 4, 4, NAV_TRAILING_PADDING));
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
        tree.addTreeSelectionListener(evt -> {
            Object last = tree.getLastSelectedPathComponent();
            if (last instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof NavNode nav && nav.cardKey() != null) {
                showPage(nav);
            }
        });

        JPanel navColumn = new JPanel(new BorderLayout(0, 4));
        navColumn.setOpaque(false);
        JLabel categoriesLabel = new JLabel(NbBundle.getMessage(UIBundle.class, "LBL_Categories"));
        categoriesLabel.setFont(categoriesLabel.getFont().deriveFont(Font.BOLD));
        navColumn.add(categoriesLabel, BorderLayout.NORTH);
        JScrollPane navScroll = new JScrollPane(tree);
        navScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        navScroll.getVerticalScrollBar().setUnitIncrement(16);
        navColumn.add(navScroll, BorderLayout.CENTER);

        detailHeader = new JLabel();
        detailHeader.setFont(detailHeader.getFont().deriveFont(Font.BOLD,
                detailHeader.getFont().getSize2D() + 2f));
        detailScroll = new JScrollPane(detailCards,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        detailScroll.setBorder(null);
        // Width follows the widest page: squeezing a page clips its checkbox labels.
        detailScroll.setPreferredSize(new Dimension(
                detailCards.getPreferredSize().width, DETAIL_PREF_HEIGHT));
        detailScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel detail = new JPanel(new BorderLayout(0, 6));
        detail.setOpaque(false);
        detail.setBorder(new EmptyBorder(0, 10, 0, 0));
        detail.add(detailHeader, BorderLayout.NORTH);
        detail.add(detailScroll, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(navColumn, BorderLayout.WEST);
        body.add(detail, BorderLayout.CENTER);
        // Same centred alignment as the scope legend: BoxLayout only hands a child the full
        // width when every child of the column shares one alignment line.
        body.setAlignmentX(Component.CENTER_ALIGNMENT);
        body.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        selectFirstPage(tree);
        return body;
    }

    /** Category tree: three groups, each holding the pages a user opens by name. */
    private DefaultMutableTreeNode buildNavModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        root.add(category("LBL_NavCoreService",
                page("LBL_ServiceHeader", "service"),
                page("LBL_BehaviorHeader", "behavior"),
                page("LBL_AppearanceHeader", "appearance")));
        root.add(category("LBL_NavSystem",
                page("LBL_GeneralHeader", "general"),
                page("LBL_ExperimentalHeader", "experimental"),
                page("LBL_LimitsHeader", "limits")));
        root.add(category("LBL_NavExtensions",
                page("LBL_FeaturesHeader", "extensions"),
                page("LBL_ContextActionsHeader", "contextActions")));
        return root;
    }

    private static DefaultMutableTreeNode category(String labelKey, DefaultMutableTreeNode... pages) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                new NavNode(NbBundle.getMessage(UIBundle.class, labelKey), null));
        for (DefaultMutableTreeNode page : pages) {
            node.add(page);
        }
        return node;
    }

    private static DefaultMutableTreeNode page(String labelKey, String cardKey) {
        return new DefaultMutableTreeNode(
                new NavNode(NbBundle.getMessage(UIBundle.class, labelKey), cardKey));
    }

    /** Registers one page with the card layout; the section keeps its own height. */
    private void addCard(String cardKey, JPanel section) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(section, BorderLayout.NORTH);
        detailCards.add(wrapper, cardKey);
    }

    /** Shows the page and names it in the header above the detail pane. */
    private void showPage(NavNode nav) {
        detailHeader.setText(NbBundle.getMessage(UIBundle.class, "LBL_SettingsHeader", nav.label()));
        ((CardLayout) detailCards.getLayout()).show(detailCards, nav.cardKey());
        detailCards.revalidate();
        if (detailScroll != null) {
            detailScroll.getVerticalScrollBar().setValue(0);
        }
    }

    /** Selects the first page so the detail pane is never blank. */
    private static void selectFirstPage(JTree tree) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            Object last = tree.getPathForRow(row).getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof NavNode nav && nav.cardKey() != null) {
                tree.setSelectionRow(row);
                return;
            }
        }
    }

    /** One tree entry: a category when {@code cardKey} is null, otherwise a settings page. */
    private record NavNode(String label, String cardKey) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** Card holder that tracks the viewport width, so the detail pane never scrolls sideways. */
    private static final class ScrollableCards extends JPanel implements Scrollable {
        ScrollableCards() {
            super(new CardLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16 * 4;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Category rows are bold; page rows are plain. Neither carries an icon. */
    private static final class NavRenderer extends DefaultTreeCellRenderer {
        NavRenderer() {
            setLeafIcon(null);
            setOpenIcon(null);
            setClosedIcon(null);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof NavNode nav) {
                setText(nav.label());
                setFont(navFont(tree, nav.cardKey() == null));
            }
            return this;
        }

        private static Font navFont(JTree tree, boolean bold) {
            Font base = UIManager.getFont("Tree.font");
            if (base == null) {
                base = tree.getFont();
            }
            return base.deriveFont(bold ? Font.BOLD : Font.PLAIN);
        }
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
        String savedPath = PlatformBridge.prefStoreSafe().get(PreferenceKeys.ACP_EXECUTABLE_PATH, null);

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

        argsField.setText(PlatformBridge.prefStoreSafe().get(PreferenceKeys.PROCESS_ARGUMENTS, "acp"));

        // Stored id wins when the path cannot explain it (custom binary or container
        // launcher); otherwise the state is Auto-detect with the path as the source.
        HarnessSelection.Selection storedChoice = HarnessSelection.resolve(
                PlatformBridge.prefStoreSafe().get(PreferenceKeys.ACP_HARNESS_ID, null),
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
        selectColor(userBubbleLightCombo, PluginSettings.getUserBubbleBackgroundLight(),
                ColorTheme.configuredBubbleColor(true, false));
        selectColor(userBubbleDarkCombo, PluginSettings.getUserBubbleBackgroundDark(),
                ColorTheme.configuredBubbleColor(true, true));
        selectColor(assistantBubbleLightCombo, PluginSettings.getAssistantBubbleBackgroundLight(),
                ColorTheme.configuredBubbleColor(false, false));
        selectColor(assistantBubbleDarkCombo, PluginSettings.getAssistantBubbleBackgroundDark(),
                ColorTheme.configuredBubbleColor(false, true));
        applyFontFamily(PluginSettings.getChatFontFamily());

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
        fsPolicyCombo.setSelectedItem(PluginSettings.getAcpFsPolicy());
        updateFsPolicyTooltip();
        runCommandAllowlist = PluginSettings.getRunCommandAllowlist();
        runCommandDenylist = PluginSettings.getRunCommandDenylist();
        updateRunCommandTooltip();
        projectSwitchRestartCheckbox.setSelected(PluginSettings.isProjectSwitchRestartConfigured());
        markdownProjectCheckbox.setSelected(PluginSettings.isMarkdownProjectEnabled());
        diffIdeFontCheckbox.setSelected(PluginSettings.isDiffIdeFontEnabled());
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
        Harness h = binName != null ? HarnessCatalog.byBinaryName(binName) : null;
        if (h != null && "gemini".equals(h.id())) {
            argsHintLabel.setText(NbBundle.getMessage(UIBundle.class, "MSG_GeminiDefaultModel"));
            argsHintLabel.setVisible(true);
        } else {
            argsHintLabel.setText("");
            argsHintLabel.setVisible(false);
        }
    }

    /** Harness detected from the current path, or {@link HarnessCatalog#UNKNOWN}. */
    private Harness detectedHarness() {
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
                Harness harness = selection.harness();
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
            Harness harness = selection.harness();
            return harness.hasLimitedSupport()
                    ? NbBundle.getMessage(UIBundle.class, "OPT_Harness_Limited", harness.displayName())
                    : harness.displayName();
        }
        Harness detected = detectedHarness();
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
        Harness detected = HarnessSelection.detect(binaryName);
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
        PlatformBridge.prefStoreSafe().put(PreferenceKeys.ACP_EXECUTABLE_PATH, pathToSave);
        PlatformBridge.prefStoreSafe().put(PreferenceKeys.PROCESS_ARGUMENTS, argsField.getText());
        // Persist the chosen harness, not just the one the file name implies: a custom
        // binary or a container launcher (docker/podman) cannot reveal the harness.
        String binName = BinaryResolver.binaryNameFromPath(pathToSave);
        HarnessSelection.Selection choice = (HarnessSelection.Selection) harnessCombo.getSelectedItem();
        PlatformBridge.prefStoreSafe().put(
                PreferenceKeys.ACP_HARNESS_ID, HarnessSelection.harnessId(choice, binName));
        PlatformBridge.prefStoreSafe().put(
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
        PluginSettings.setDiffIdeFontEnabled(diffIdeFontCheckbox.isSelected());
        PluginSettings.setMcpServerEnabled(mcpServerCheckbox.isSelected());
        PluginSettings.setMcpServerPort((Integer) mcpPortSpinner.getValue());
        PluginSettings.setUseWsl(useWslCheckbox.isSelected());

        String newIconPath = iconPathField.getText();
        String oldPath = previousIconPath != null ? previousIconPath : "";
        String newPath = newIconPath != null ? newIconPath : "";
        boolean iconChanged = !oldPath.equals(newPath);
        if (iconChanged) {
            LOG.info("User icon changed: {0} -> {1}", oldPath, newPath);
            previousIconPath = newPath;
        }
        boolean appearanceChanged = writeAppearanceSettings();
        if (iconChanged || appearanceChanged) {
            // Both re-render the thread: a new avatar, bubble colour or font only shows on a
            // fresh load, so reload the current session once.
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
