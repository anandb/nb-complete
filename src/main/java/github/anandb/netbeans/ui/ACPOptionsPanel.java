package github.anandb.netbeans.ui;

import java.io.File;

import javax.swing.JFileChooser;

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
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.contract.SessionControl;
import org.openide.util.Lookup;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PreferenceKeys;

public class ACPOptionsPanel extends JPanel {
    private static final Logger LOG = Logger.from(ACPOptionsPanel.class);
    private static final long serialVersionUID = 1L;
    private final ACPOptionsPanelController controller;
    private JLabel jLabel1;
    private JTextField pathField;
    private JLabel preambleLabel;
    private JTextArea preambleArea;
    private JScrollPane preambleScroll;
    private JButton browseButton;
    private JCheckBox echoCheckbox;
    private JCheckBox combineCheckbox;
    private JSpinner idleTimeoutSpinner;
    private JLabel argsLabel;
    private JTextField argsField;
    private JLabel iconLabel;
    private JTextField iconPathField;
    private JButton iconBrowseButton;
    private JLabel iconPreviewLabel;
    private IconPreviewManager iconPreviewManager;

    private String detectedPath;
    private boolean showingHint;

    private static final Color HINT_COLOR = Color.GRAY;

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
            public void keyReleased(KeyEvent evt) { controller.changed(); }
        });
        pathField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) { clearHint(); }

            @Override
            public void focusLost(FocusEvent e) { restoreHintIfEmpty(); }
        });
        add(pathField, UIUtils.createGbc(1, 1, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        browseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        browseButton.addActionListener(evt -> browseButtonActionPerformed());
        add(browseButton, UIUtils.createGbc(2, 1, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                            new Insets(0, 0, 5, 0)));

        argsLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ProcessArguments"));
        add(argsLabel, UIUtils.createGbc(0, 2, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                         new Insets(0, 12, 15, 5)));

        argsField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) { controller.changed(); }
        });
        add(argsField, UIUtils.createGbc(1, 2, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                         GridBagConstraints.WEST, new Insets(0, 0, 15, 5)));

        // --- SECTION: Chat Behavior ---
        JLabel behaviorHeader = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_BehaviorHeader"));
        behaviorHeader.setFont(behaviorHeader.getFont().deriveFont(Font.BOLD));
        GridBagConstraints gbcHeader = UIUtils.createGbc(0, 3, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(10, 0, 10, 0));
        gbcHeader.gridwidth = 3;
        add(behaviorHeader, gbcHeader);

        echoCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EchoUserInput"));
        echoCheckbox.addActionListener(evt -> controller.changed());
        add(echoCheckbox, UIUtils.createGbc(0, 4, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 0)));

        combineCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_CombineToolThought"));
        combineCheckbox.addActionListener(evt -> controller.changed());
        add(combineCheckbox, UIUtils.createGbc(0, 5, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(0, 12, 10, 0)));

        JLabel idleTimeoutLabel = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_SessionIdleTimeout"));
        add(idleTimeoutLabel, UIUtils.createGbc(0, 6, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 12, 10, 5)));
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(60, 0, 3600, 5);
        idleTimeoutSpinner = new JSpinner(spinnerModel);
        idleTimeoutSpinner.addChangeListener(evt -> controller.changed());
        add(idleTimeoutSpinner, UIUtils.createGbc(1, 6, 0.0, 0, GridBagConstraints.NONE,
                GridBagConstraints.WEST, new Insets(0, 0, 10, 0)));

        preambleLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_Preamble"));
        GridBagConstraints gbcPreambleLabel = UIUtils.createGbc(0, 7, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0));
        gbcPreambleLabel.gridwidth = 3;
        add(preambleLabel, gbcPreambleLabel);

        preambleArea.setLineWrap(true);
        preambleArea.setWrapStyleWord(true);
        preambleArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) { controller.changed(); }
        });
        GridBagConstraints gbcPreambleScroll = UIUtils.createGbc(0, 8, 1.0, 0.2, GridBagConstraints.BOTH,
                GridBagConstraints.WEST, new Insets(0, 12, 15, 0));
        gbcPreambleScroll.gridwidth = 3;
        add(preambleScroll, gbcPreambleScroll);

        // --- SECTION: Appearance ---
        JLabel appearanceHeader = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_AppearanceHeader"));
        appearanceHeader.setFont(appearanceHeader.getFont().deriveFont(Font.BOLD));
        GridBagConstraints gbcAppHeader = UIUtils.createGbc(0, 9, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(10, 0, 10, 0));
        gbcAppHeader.gridwidth = 3;
        add(appearanceHeader, gbcAppHeader);

        iconLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_UserIcon"));
        add(iconLabel, UIUtils.createGbc(0, 10, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 5)));

        iconPathField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) {
                iconPreviewManager.updatePreview(iconPathField.getText());
                controller.changed();
            }
        });
        add(iconPathField, UIUtils.createGbc(1, 10, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                             GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        iconBrowseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        iconBrowseButton.addActionListener(evt -> iconBrowseButtonActionPerformed());
        add(iconBrowseButton, UIUtils.createGbc(2, 10, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.WEST, new Insets(0, 0, 5, 0)));

        add(iconPreviewLabel, UIUtils.createGbc(1, 11, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.WEST, new Insets(5, 0, 5, 0)));

        // Spacer at the bottom to push everything up
        add(new JLabel(), UIUtils.createGbc(0, 12, 1.0, 1.0, GridBagConstraints.BOTH,
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
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            controller.changed();
        }
    }

    private void iconBrowseButtonActionPerformed() {
        JFileChooser chooser = new JFileChooser();
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
        idleTimeoutSpinner.setValue(PluginSettings.getSessionIdleTimeout());
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
        PluginSettings.setSessionIdleTimeout((Integer) idleTimeoutSpinner.getValue());

        if (changedCombine) {
            LOG.info("Combine tool/thought toggled to {0}", combineCheckbox.isSelected());
            String currentId = Lookup.getDefault().lookup(SessionControl.class).getCurrentSessionId();
            if (currentId != null) {
                SwingUtilities.invokeLater(() -> Lookup.getDefault().lookup(SessionControl.class).loadSession(currentId));
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
                SessionControl sm = Lookup.getDefault().lookup(SessionControl.class);
                String currentId = sm.getCurrentSessionId();
                if (currentId != null) {
                    sm.loadSession(currentId);
                }
            });
        }
    }

    boolean valid() {
        return true;
    }
}
