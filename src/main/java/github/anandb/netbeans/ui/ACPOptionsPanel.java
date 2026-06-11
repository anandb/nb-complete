package github.anandb.netbeans.ui;

import java.io.File;

import javax.swing.JFileChooser;

import github.anandb.netbeans.manager.BinaryResolver;
import github.anandb.netbeans.manager.PluginSettings;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

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

        // --- SECTION: Assistant Service ---
        JLabel serviceHeader = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ServiceHeader"));
        serviceHeader.setFont(serviceHeader.getFont().deriveFont(Font.BOLD));
        add(serviceHeader, UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                             GridBagConstraints.WEST, new Insets(0, 0, 10, 0)));

        jLabel1.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ExecutablePath"));
        add(jLabel1, UIUtils.createGbc(0, 1, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                                       new Insets(0, 12, 5, 5)));

        pathField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) { controller.changed(); }
        });
        pathField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                clearHint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                restoreHintIfEmpty();
            }
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
        add(behaviorHeader, UIUtils.createGbc(0, 3, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST,
                new Insets(10, 0, 10, 0)));

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

        // GridBag uses gridwidth to span columns
        GridBagConstraints gbcHeader = UIUtils.createGbc(0, 3, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(10, 0, 10, 0));
        gbcHeader.gridwidth = 3;
        ((GridBagLayout)getLayout()).setConstraints(behaviorHeader, gbcHeader);

        GridBagConstraints gbcServiceHeader = UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 0, 10, 0));
        gbcServiceHeader.gridwidth = 3;
        ((GridBagLayout)getLayout()).setConstraints(serviceHeader, gbcServiceHeader);

        preambleLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_Preamble"));
        add(preambleLabel, UIUtils.createGbc(0, 7, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));
        GridBagConstraints gbcPreambleLabel = UIUtils.createGbc(0, 7, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(0, 12, 5, 0));
        gbcPreambleLabel.gridwidth = 3;
        ((GridBagLayout)getLayout()).setConstraints(preambleLabel, gbcPreambleLabel);

        preambleArea.setLineWrap(true);
        preambleArea.setWrapStyleWord(true);
        preambleArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) { controller.changed(); }
        });
        add(preambleScroll, UIUtils.createGbc(0, 8, 1.0, 0.2, GridBagConstraints.BOTH,
                GridBagConstraints.WEST, new Insets(0, 12, 15, 0)));
        GridBagConstraints gbcPreambleScroll = UIUtils.createGbc(0, 8, 1.0, 0.2, GridBagConstraints.BOTH,
                GridBagConstraints.WEST, new Insets(0, 12, 15, 0));
        gbcPreambleScroll.gridwidth = 3;
        ((GridBagLayout)getLayout()).setConstraints(preambleScroll, gbcPreambleScroll);

        // --- SECTION: Appearance ---
        JLabel appearanceHeader = new JLabel(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_AppearanceHeader"));
        appearanceHeader.setFont(appearanceHeader.getFont().deriveFont(Font.BOLD));
        add(appearanceHeader, UIUtils.createGbc(0, 9, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(10, 0, 10, 0)));
        GridBagConstraints gbcAppHeader = UIUtils.createGbc(0, 9, 1.0, 0, GridBagConstraints.HORIZONTAL,
                GridBagConstraints.WEST, new Insets(10, 0, 10, 0));
        gbcAppHeader.gridwidth = 3;
        ((GridBagLayout) getLayout()).setConstraints(appearanceHeader, gbcAppHeader);

        iconLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_UserIcon"));
        add(iconLabel, UIUtils.createGbc(0, 10, 0.0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST,
                new Insets(0, 12, 5, 5)));

        iconPathField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) {
                updateIconPreview(iconPathField.getText());
                controller.changed();
            }
        });
        add(iconPathField, UIUtils.createGbc(1, 10, 1.0, 0, GridBagConstraints.HORIZONTAL,
                                             GridBagConstraints.WEST, new Insets(0, 0, 5, 5)));

        iconBrowseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        iconBrowseButton.addActionListener(evt -> iconBrowseButtonActionPerformed());
        add(iconBrowseButton, UIUtils.createGbc(2, 10, 0.0, 0, GridBagConstraints.NONE,
                                                GridBagConstraints.WEST, new Insets(0, 0, 5, 0)));

        iconPreviewLabel.setPreferredSize(new Dimension(80, 80));
        iconPreviewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        iconPreviewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconPreviewLabel.setToolTipText(NbBundle.getMessage(ACPOptionsPanel.class, "TIP_ClearIcon"));
        iconPreviewLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }

            private void showPopup(MouseEvent e) {
                if (iconPathField.getText().isEmpty()) return;
                JPopupMenu popup = new JPopupMenu();
                JMenuItem clearItem = new JMenuItem(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_Clear"));
                clearItem.addActionListener(evt -> {
                    iconPathField.setText("");
                    updateIconPreview("");
                    controller.changed();
                });
                popup.add(clearItem);
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
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
        chooser.setFileFilter(new FileNameExtensionFilter("Image files (SVG, PNG)", "svg", "png"));
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
            updateIconPreview(path);
            controller.changed();
        }
    }

    private void updateIconPreview(String path) {
        if (path == null || path.isEmpty()) {
            iconPreviewLabel.setIcon(null);
            iconPreviewLabel.setText("");
            return;
        }
        File file = new File(path);
        if (!file.exists()) {
            iconPreviewLabel.setIcon(null);
            iconPreviewLabel.setText("");
            return;
        }
        try {
            ImageIcon icon = new ImageIcon(path);
            if (icon.getIconWidth() > 0) {
                iconPreviewLabel.setIcon(new ImageIcon(
                    icon.getImage().getScaledInstance(72, 72, Image.SCALE_SMOOTH)));
                iconPreviewLabel.setText("");
            } else {
                iconPreviewLabel.setIcon(null);
                iconPreviewLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_SVGPreview"));
            }
        } catch (Exception e) {
            LOG.warn("Failed to update icon preview for: {0}", path, e);
            iconPreviewLabel.setIcon(null);
            iconPreviewLabel.setText("?");
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
        updateIconPreview(iconPathField.getText());
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

        // Reload session if combine preference changed
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
