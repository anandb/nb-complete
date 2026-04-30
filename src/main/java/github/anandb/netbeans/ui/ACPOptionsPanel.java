package github.anandb.netbeans.ui;

import java.io.File;

import javax.swing.JFileChooser;

import github.anandb.netbeans.manager.ACPSettings;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.support.Logger;

@NbBundle.Messages({
    "LBL_ExecutablePath=Path to Opencode Binary:",
    "LBL_ProcessArguments=Process Arguments:",
    "BTN_Browse=Browse...",
    "TITLE_SelectExecutable=Select Assistant Executable",
    "LBL_Preamble=Session preamble (sent as first user message for new chats, use '---' as a message separator):",
    "LBL_EchoUserInput=Echo user input immediately (Local Echo)",
    "LBL_UserIcon=Custom User Icon (SVG or PNG):",
    "TITLE_SelectIcon=Select User Icon",
    "LBL_Clear=Clear",
    "TIP_ClearIcon=Right-click to clear custom icon"
})
public class ACPOptionsPanel extends javax.swing.JPanel {
    private static final Logger LOG = new Logger(ACPOptionsPanel.class);

    private static final long serialVersionUID = 1L;
    private final ACPOptionsPanelController controller;
    private JLabel jLabel1;
    private JTextField pathField;
    private JLabel preambleLabel;
    private JTextArea preambleArea;
    private JScrollPane preambleScroll;
    private JButton browseButton;
    private JCheckBox echoCheckbox;
    private JLabel argsLabel;
    private JTextField argsField;
    private JLabel iconLabel;
    private JTextField iconPathField;
    private JButton iconBrowseButton;
    private JLabel iconPreviewLabel;

    ACPOptionsPanel(ACPOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
    }

    private void initComponents() {
        setLayout(new java.awt.GridBagLayout());

        jLabel1 = new JLabel();
        pathField = new JTextField();
        browseButton = new JButton();
        argsLabel = new JLabel();
        argsField = new JTextField();
        echoCheckbox = new JCheckBox();
        preambleLabel = new JLabel();
        preambleArea = new JTextArea(5, 40);
        preambleScroll = new JScrollPane(preambleArea);
        iconLabel = new JLabel();
        iconPathField = new JTextField();
        iconBrowseButton = new JButton();
        iconPreviewLabel = new JLabel();

        // --- SECTION: Assistant Service ---
        JLabel serviceHeader = new JLabel("Assistant Service");
        serviceHeader.setFont(serviceHeader.getFont().deriveFont(java.awt.Font.BOLD));
        add(serviceHeader, UIUtils.createGbc(0, 0, 1.0, 0, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, new java.awt.Insets(0, 0, 10, 0)));

        jLabel1.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ExecutablePath"));
        add(jLabel1, UIUtils.createGbc(0, 1, 0.0, 0, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, new java.awt.Insets(0, 12, 5, 5)));

        pathField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) { controller.changed(); }
        });
        add(pathField, UIUtils.createGbc(1, 1, 1.0, 0, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, new java.awt.Insets(0, 0, 5, 5)));

        browseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        browseButton.addActionListener(evt -> browseButtonActionPerformed());
        add(browseButton, UIUtils.createGbc(2, 1, 0.0, 0, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, new java.awt.Insets(0, 0, 5, 0)));

        argsLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ProcessArguments"));
        add(argsLabel, UIUtils.createGbc(0, 2, 0.0, 0, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, new java.awt.Insets(0, 12, 15, 5)));

        argsField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) { controller.changed(); }
        });
        add(argsField, UIUtils.createGbc(1, 2, 1.0, 0, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, new java.awt.Insets(0, 0, 15, 5)));

        // --- SECTION: Chat Behavior ---
        JLabel behaviorHeader = new JLabel("Chat Behavior");
        behaviorHeader.setFont(behaviorHeader.getFont().deriveFont(java.awt.Font.BOLD));
        add(behaviorHeader, UIUtils.createGbc(0, 3, 1.0, 0, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, new java.awt.Insets(10, 0, 10, 0)));

        echoCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EchoUserInput"));
        echoCheckbox.addActionListener(evt -> controller.changed());
        add(echoCheckbox, UIUtils.createGbc(0, 4, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST, new Insets(0, 12, 10, 0)));

        // GridBag uses gridwidth to span columns
        GridBagConstraints gbcHeader = UIUtils.createGbc(0, 3, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST, new Insets(10, 0, 10, 0));
        gbcHeader.gridwidth = 3;
        ((GridBagLayout)getLayout()).setConstraints(behaviorHeader, gbcHeader);

        GridBagConstraints gbcServiceHeader = UIUtils.createGbc(0, 0, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST, new Insets(0, 0, 10, 0));
        gbcServiceHeader.gridwidth = 3;
        ((GridBagLayout)getLayout()).setConstraints(serviceHeader, gbcServiceHeader);

        preambleLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_Preamble"));
        add(preambleLabel, UIUtils.createGbc(0, 5, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST, new Insets(0, 12, 5, 0)));
        GridBagConstraints gbcPreambleLabel = UIUtils.createGbc(0, 5, 1.0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST, new Insets(0, 12, 5, 0));
        gbcPreambleLabel.gridwidth = 3;
        ((GridBagLayout)getLayout()).setConstraints(preambleLabel, gbcPreambleLabel);

        preambleArea.setLineWrap(true);
        preambleArea.setWrapStyleWord(true);
        preambleArea.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) { controller.changed(); }
        });
        add(preambleScroll, UIUtils.createGbc(0, 6, 1.0, 0.2, GridBagConstraints.BOTH, GridBagConstraints.WEST, new Insets(0, 12, 15, 0)));
        GridBagConstraints gbcPreambleScroll = UIUtils.createGbc(0, 6, 1.0, 0.2, GridBagConstraints.BOTH, GridBagConstraints.WEST, new Insets(0, 12, 15, 0));
        gbcPreambleScroll.gridwidth = 3;
        ((GridBagLayout)getLayout()).setConstraints(preambleScroll, gbcPreambleScroll);

        // --- SECTION: Appearance ---
        JLabel appearanceHeader = new JLabel("Appearance");
        appearanceHeader.setFont(appearanceHeader.getFont().deriveFont(java.awt.Font.BOLD));
        add(appearanceHeader, UIUtils.createGbc(0, 7, 1.0, 0, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, new java.awt.Insets(10, 0, 10, 0)));
        java.awt.GridBagConstraints gbcAppHeader = UIUtils.createGbc(0, 7, 1.0, 0, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, new java.awt.Insets(10, 0, 10, 0));
        gbcAppHeader.gridwidth = 3;
        ((java.awt.GridBagLayout)getLayout()).setConstraints(appearanceHeader, gbcAppHeader);

        iconLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_UserIcon"));
        add(iconLabel, UIUtils.createGbc(0, 8, 0.0, 0, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, new java.awt.Insets(0, 12, 5, 5)));

        iconPathField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                updateIconPreview(iconPathField.getText());
                controller.changed();
            }
        });
        add(iconPathField, UIUtils.createGbc(1, 8, 1.0, 0, java.awt.GridBagConstraints.HORIZONTAL, java.awt.GridBagConstraints.WEST, new java.awt.Insets(0, 0, 5, 5)));

        iconBrowseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        iconBrowseButton.addActionListener(evt -> iconBrowseButtonActionPerformed());
        add(iconBrowseButton, UIUtils.createGbc(2, 8, 0.0, 0, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, new java.awt.Insets(0, 0, 5, 0)));

        iconPreviewLabel.setPreferredSize(new java.awt.Dimension(80, 80));
        iconPreviewLabel.setBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY));
        iconPreviewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconPreviewLabel.setToolTipText(NbBundle.getMessage(ACPOptionsPanel.class, "TIP_ClearIcon"));
        iconPreviewLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
            private void showPopup(java.awt.event.MouseEvent e) {
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
        add(iconPreviewLabel, UIUtils.createGbc(1, 9, 0.0, 0, java.awt.GridBagConstraints.NONE, java.awt.GridBagConstraints.WEST, new java.awt.Insets(5, 0, 5, 0)));

        // Spacer at the bottom to push everything up
        add(new JLabel(), UIUtils.createGbc(0, 10, 1.0, 1.0, java.awt.GridBagConstraints.BOTH, java.awt.GridBagConstraints.NORTHWEST, new java.awt.Insets(0, 0, 0, 0)));
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
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            controller.changed();
        }
    }

    private void iconBrowseButtonActionPerformed() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Image files (SVG, PNG)", "svg", "png"));
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
                    icon.getImage().getScaledInstance(72, 72, java.awt.Image.SCALE_SMOOTH)));
                iconPreviewLabel.setText("");
            } else {
                iconPreviewLabel.setIcon(null);
                iconPreviewLabel.setText("SVG");
            }
        } catch (Exception e) {
            LOG.warn("Failed to update icon preview for: {0}", path, e);
            iconPreviewLabel.setIcon(null);
            iconPreviewLabel.setText("?");
        }
    }

    private String previousIconPath;

    void load() {
        String defaultPath = System.getProperty("user.home") + "/.opencode/bin/opencode";
        pathField.setText(NbPreferences.forModule(ACPOptionsPanel.class).get("acpExecutablePath", defaultPath));
        argsField.setText(NbPreferences.forModule(ACPOptionsPanel.class).get("processArguments", "acp"));

        preambleArea.setText(ACPSettings.getPreamble());
        echoCheckbox.setSelected(NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("echoUserInput", true));
        previousIconPath = ACPSettings.getCustomUserIcon();
        iconPathField.setText(previousIconPath);
        updateIconPreview(iconPathField.getText());
    }

    void store() {
        NbPreferences.forModule(ACPOptionsPanel.class).put("acpExecutablePath", pathField.getText());
        NbPreferences.forModule(ACPOptionsPanel.class).put("processArguments", argsField.getText());
        ACPSettings.setPreamble(preambleArea.getText());
        NbPreferences.forModule(ACPOptionsPanel.class).putBoolean("echoUserInput", echoCheckbox.isSelected());
        ACPSettings.setCustomUserIcon(iconPathField.getText());

        String newIconPath = iconPathField.getText();
        String oldPath = previousIconPath != null ? previousIconPath : "";
        String newPath = newIconPath != null ? newIconPath : "";
        if (!oldPath.equals(newPath)) {
            LOG.info("User icon changed: {0} -> {1}", oldPath, newPath);
            previousIconPath = newPath;
            SwingUtilities.invokeLater(() -> {
                github.anandb.netbeans.manager.SessionManager sm = github.anandb.netbeans.manager.SessionManager.getInstance();
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
