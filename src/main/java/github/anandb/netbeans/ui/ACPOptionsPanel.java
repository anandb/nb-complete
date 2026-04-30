package github.anandb.netbeans.ui;

import java.io.File;

import javax.swing.JFileChooser;

import github.anandb.netbeans.manager.ACPSettings;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

@NbBundle.Messages({
    "LBL_ExecutablePath=Executable Path:",
    "LBL_ProcessArguments=Process Arguments:",
    "BTN_Browse=Browse...",
    "TITLE_SelectExecutable=Select Assistant Executable",
    "LBL_Preamble=Session preamble (sent as first user message for new chats):",
    "LBL_EchoUserInput=Echo user input immediately (Local Echo)",
    "LBL_UserIcon=Custom User Icon (SVG or PNG):",
    "TITLE_SelectIcon=Select User Icon",
    "LBL_Clear=Clear",
    "TIP_ClearIcon=Right-click to clear custom icon"
})
public class ACPOptionsPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private final ACPOptionsPanelController controller;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextField pathField;
    private javax.swing.JLabel preambleLabel;
    private javax.swing.JTextArea preambleArea;
    private javax.swing.JScrollPane preambleScroll;
    private javax.swing.JButton browseButton;
    private javax.swing.JCheckBox echoCheckbox;
    private javax.swing.JLabel argsLabel;
    private javax.swing.JTextField argsField;
    private javax.swing.JLabel iconLabel;
    private javax.swing.JTextField iconPathField;
    private javax.swing.JButton iconBrowseButton;
    private javax.swing.JLabel iconPreviewLabel;

    ACPOptionsPanel(ACPOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
    }

    private void initComponents() {
        jLabel1 = new javax.swing.JLabel();
        pathField = new javax.swing.JTextField();
        preambleLabel = new javax.swing.JLabel();
        preambleArea = new javax.swing.JTextArea(3, 40);
        preambleScroll = new javax.swing.JScrollPane(preambleArea);
        browseButton = new javax.swing.JButton();
        argsLabel = new javax.swing.JLabel();
        argsField = new javax.swing.JTextField();
        echoCheckbox = new javax.swing.JCheckBox();
        iconLabel = new javax.swing.JLabel();
        iconPathField = new javax.swing.JTextField();
        iconBrowseButton = new javax.swing.JButton();
        iconPreviewLabel = new javax.swing.JLabel();

        jLabel1.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ExecutablePath"));
        argsLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ProcessArguments"));
        browseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        browseButton.addActionListener(evt -> browseButtonActionPerformed());

        echoCheckbox.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_EchoUserInput"));
        echoCheckbox.addActionListener(evt -> controller.changed());

        iconLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_UserIcon"));
        iconBrowseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        iconBrowseButton.addActionListener(evt -> iconBrowseButtonActionPerformed());
        iconPathField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                updateIconPreview(iconPathField.getText());
                controller.changed();
            }
        });
        iconPreviewLabel.setPreferredSize(new java.awt.Dimension(40, 40));
        iconPreviewLabel.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.GRAY));
        iconPreviewLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        iconPreviewLabel.setToolTipText(NbBundle.getMessage(ACPOptionsPanel.class, "TIP_ClearIcon"));
        iconPreviewLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }
            private void showPopup(java.awt.event.MouseEvent e) {
                if (iconPathField.getText().isEmpty()) {
                    return;
                }
                javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
                javax.swing.JMenuItem clearItem = new javax.swing.JMenuItem(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_Clear"));
                clearItem.addActionListener(evt -> {
                    iconPathField.setText("");
                    updateIconPreview("");
                    controller.changed();
                });
                popup.add(clearItem);
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        argsField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                controller.changed();
            }
        });

        preambleLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_Preamble"));
        preambleArea.setLineWrap(true);
        preambleArea.setWrapStyleWord(true);
        preambleArea.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                controller.changed();
            }
        });

        pathField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                controller.changed();
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pathField, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseButton))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(argsLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(argsField, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE))
                    .addComponent(echoCheckbox)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(iconLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(iconPathField, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(iconBrowseButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(iconPreviewLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(preambleLabel)
                    .addComponent(preambleScroll))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(pathField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(browseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(argsLabel)
                    .addComponent(argsField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(echoCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(iconLabel)
                    .addComponent(iconPathField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(iconBrowseButton)
                    .addComponent(iconPreviewLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(preambleLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(preambleScroll, javax.swing.GroupLayout.DEFAULT_SIZE, 70, Short.MAX_VALUE)
                .addContainerGap())
        );
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
            javax.swing.ImageIcon icon = new javax.swing.ImageIcon(path);
            if (icon.getIconWidth() > 0) {
                iconPreviewLabel.setIcon(new javax.swing.ImageIcon(
                    icon.getImage().getScaledInstance(36, 36, java.awt.Image.SCALE_SMOOTH)));
                iconPreviewLabel.setText("");
            } else {
                iconPreviewLabel.setIcon(null);
                iconPreviewLabel.setText("SVG");
            }
        } catch (Exception e) {
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
            previousIconPath = newPath;
            javax.swing.SwingUtilities.invokeLater(() -> {
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
