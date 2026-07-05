package github.anandb.netbeans.ui;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.PluginSettings;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import org.openide.util.Lookup;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.SessionService;

// DSL-LEAF: keep imperative, wrap via UI.of(...) — NetBeans Options panel
// (GridBagLayout form). When the DSL lands, port the form to OptionsFormSpec;
// ACPOptionsPanelController (NetBeans SPI) stays as-is.
public class ACPOptionsPanel extends JPanel {
    private static final Logger LOG = Logger.from(ACPOptionsPanel.class);
    private static final long serialVersionUID = 1L;
    private final SessionService sessionService = Lookup.getDefault().lookup(PlatformBridge.class).sessionService();
    private final ACPOptionsPanelController controller;
    private JLabel jLabel1;
    private JTextField pathField;
    private JLabel pathErrorLabel;
    private JLabel preambleLabel;
    private JTextArea preambleArea;
    private JScrollPane preambleScroll;
    private JButton browseButton;
    private JCheckBox echoCheckbox;
    private JCheckBox combineCheckbox;
    private JCheckBox checkForUpdatesCheckbox;
    private JSpinner idleTimeoutSpinner;
    private JSpinner maxMessagesSpinner;
    private JLabel argsLabel;
    private JTextField argsField;
    private JLabel iconLabel;
    private JTextField iconPathField;
    private JButton iconBrowseButton;
    private JLabel iconPreviewLabel;
    private transient IconPreviewManager iconPreviewManager;

    private String detectedPath;
    private boolean showingHint;
    private boolean userEditedPath;

    private static final Color HINT_COLOR = ThemeManager.getCurrentTheme().mutedForeground();

    ACPOptionsPanel(ACPOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());

        jLabel1 = new JLabel();
        pathField = new JTextField(40);
        browseButton = new JButton();
        argsLabel = new JLabel();
        argsField = new JTextField(40);
        echoCheckbox = new JCheckBox();
        combineCheckbox = new JCheckBox();
        checkForUpdatesCheckbox = new JCheckBox();
        preambleLabel = new JLabel();
        preambleArea = new JTextArea(5, 40);
        preambleScroll = new JScrollPane(preambleArea);
        iconLabel = new JLabel();
        iconPathField = new JTextField(40);
        iconBrowseButton = new JButton();
        iconPreviewLabel = new JLabel();

        iconPreviewManager = new IconPreviewManager(iconPreviewLabel, iconPathField, controller::changed);

        // --- SECTION: Assistant Service ---
        JLabel serviceHeader = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ServiceHeader"));
        serviceHeader.setFont(serviceHeader.getFont().deriveFont(Font.BOLD));
        GridBagConstraints gbcServiceHeader = UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 0, 10, 0));
        gbcServiceHeader.gridwidth = 3;
        add(serviceHeader, gbcServiceHeader);

        jLabel1.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ExecutablePath"));
        add(jLabel1, UIUtils.createGbc(0, 1, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
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
        add(pathField, UIUtils.createGbc(1, 1, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 0, 5)));

        pathErrorLabel = new JLabel(" ");
        pathErrorLabel.setForeground(UIManager.getColor("Panel.foreground"));
        pathErrorLabel.setFont(pathErrorLabel.getFont().deriveFont(Font.ITALIC, pathErrorLabel.getFont().getSize() - 1f));
        GridBagConstraints errGbc = UIUtils.createGbc(1, 2, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 5, 5));
        errGbc.gridwidth = 2;
        add(pathErrorLabel, errGbc);

        browseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        browseButton.addActionListener(evt -> browseButtonActionPerformed());
        add(browseButton, UIUtils.createGbc(2, 1, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                            new Insets(0, 0, 5, 0)));

        argsLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ProcessArguments"));
        add(argsLabel, UIUtils.createGbc(0, 3, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                         new Insets(0, 12, 5, 5)));

        argsField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) { controller.changed(); }
        });
        add(argsField, UIUtils.createGbc(1, 3, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

// --- SECTION: Updates ---
        JLabel updatesHeader = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_UpdatesHeader"));
        updatesHeader.setFont(updatesHeader.getFont().deriveFont(Font.BOLD));
        GridBagConstraints gbcUpdatesHeader = UIUtils.createGbc(0, 4, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(10, 0, 10, 0));
        gbcUpdatesHeader.gridwidth = 3;
        add(updatesHeader, gbcUpdatesHeader);

        checkForUpdatesCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_CheckForUpdates"));
        checkForUpdatesCheckbox.addActionListener(evt -> controller.changed());
        GridBagConstraints gbcUpdates = UIUtils.createGbc(0, 5, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 15, 0));
        gbcUpdates.gridwidth = 3;
        add(checkForUpdatesCheckbox, gbcUpdates);

        // --- SECTION: Chat Behavior ---
        JLabel behaviorHeader = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_BehaviorHeader"));
        behaviorHeader.setFont(behaviorHeader.getFont().deriveFont(Font.BOLD));
        GridBagConstraints gbcHeader = UIUtils.createGbc(0, 6, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(10, 0, 10, 0));
        gbcHeader.gridwidth = 3;
        add(behaviorHeader, gbcHeader);

        echoCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EchoInput"));
        echoCheckbox.addActionListener(evt -> controller.changed());
        add(echoCheckbox, UIUtils.createGbc(0, 7, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 10, 0)));

        combineCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_CombineToolThought"));
        combineCheckbox.addActionListener(evt -> controller.changed());
        add(combineCheckbox, UIUtils.createGbc(0, 8, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 10, 0)));

        JLabel idleTimeoutLabel = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_SessionIdleTimeout"));
        add(idleTimeoutLabel, UIUtils.createGbc(0, 9, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 12, 10, 5)));
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(300, 0, 3600, 5);
        idleTimeoutSpinner = new JSpinner(spinnerModel);
        idleTimeoutSpinner.addChangeListener(evt -> controller.changed());
        add(idleTimeoutSpinner, UIUtils.createGbc(1, 9, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 0, 10, 0)));

        JLabel maxMessagesLabel = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_MaxMessages"));
        add(maxMessagesLabel, UIUtils.createGbc(0, 10, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 12, 10, 5)));
        SpinnerNumberModel maxMessagesModel = new SpinnerNumberModel(100, 10, 100, 5);
        maxMessagesSpinner = new JSpinner(maxMessagesModel);
        maxMessagesSpinner.addChangeListener(evt -> controller.changed());
        add(maxMessagesSpinner, UIUtils.createGbc(1, 10, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 0, 10, 0)));

        // --- SECTION: Appearance ---
        // Appearance section moved above the preamble so the preamble text
        // area can occupy the bottom of the form and absorb all leftover
        // vertical space when the Options window is resized taller.
        JLabel appearanceHeader = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_AppearanceHeader"));
        appearanceHeader.setFont(appearanceHeader.getFont().deriveFont(Font.BOLD));
        GridBagConstraints gbcAppHeader = UIUtils.createGbc(0, 11, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(10, 0, 10, 0));
        gbcAppHeader.gridwidth = 3;
        add(appearanceHeader, gbcAppHeader);

        iconLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_UserIcon"));
        add(iconLabel, UIUtils.createGbc(0, 12, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 5)));

        iconPathField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) {
                iconPreviewManager.updatePreview(iconPathField.getText());
                controller.changed();
            }
        });
        add(iconPathField, UIUtils.createGbc(1, 12, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                             GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        iconBrowseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        iconBrowseButton.addActionListener(evt -> iconBrowseButtonActionPerformed());
        add(iconBrowseButton, UIUtils.createGbc(2, 12, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.WEST, new Insets(0, 0, 5, 0)));

        add(iconPreviewLabel, UIUtils.createGbc(1, 13, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.WEST, new Insets(5, 0, 5, 0)));

        // --- SECTION: Session preamble (bottom-most, grows with window) ---
        preambleLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_Preamble"));
        GridBagConstraints gbcPreambleLabel = UIUtils.createGbc(0, 14, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(10, 12, 5, 0));
        gbcPreambleLabel.gridwidth = 3;
        add(preambleLabel, gbcPreambleLabel);

        preambleArea.setLineWrap(true);
        preambleArea.setWrapStyleWord(true);
        preambleArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) { controller.changed(); }
        });
        // Right-click menu: Clear or Reset to default preamble
        JPopupMenu preambleMenu = new JPopupMenu();
        JMenuItem clearItem = new JMenuItem(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_PreambleClear"));
        clearItem.addActionListener(e -> preambleArea.setText(""));
        preambleMenu.add(clearItem);
        JMenuItem resetItem = new JMenuItem(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_PreambleReset"));
        resetItem.addActionListener(e -> preambleArea.setText(PluginSettings.getDefaultPreamble()));
        preambleMenu.add(resetItem);
        preambleArea.setComponentPopupMenu(preambleMenu);
        // weighty=1.0 + BOTH: absorbs all leftover vertical space at the
        // bottom of the form. The spacer at row 15 has weighty=0 so the
        // preamble takes priority on window resize.
        // weighty=1.0 + BOTH: absorbs all leftover vertical space at the
        // bottom of the form. The spacer at row 16 has weighty=0 so the
        // preamble takes priority on window resize.
        GridBagConstraints gbcPreambleScroll = UIUtils.createGbc(0, 15, 1.0, 1.0, GridBagConstraints.BOTH,
                GridBagConstraints.WEST, new Insets(0, 12, 15, 0));
        gbcPreambleScroll.gridwidth = 3;
        add(preambleScroll, gbcPreambleScroll);

        // Spacer at the bottom — keeps the layout anchored above the preamble
        // (which now absorbs all leftover vertical space at row 14 weighty=1.0).
        // weighty=0 here so the preamble takes priority; a non-zero value would
        // split leftover space evenly with the preamble and shrink it.
        add(new JLabel(), UIUtils.createGbc(0, 16, 1.0, 0.0, GridBagConstraints.BOTH,
                                            GridBagConstraints.NORTHWEST, new Insets(0, 0, 0, 0)));
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
            pathErrorLabel.setText(" ");
            return true;
        }
        String path = pathField.getText();
        if (path == null || path.trim().isEmpty()) {
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
        pathErrorLabel.setText(" ");
        return true;
    }
}
