package ai.opencode.netbeans.ui;

import java.io.File;
import javax.swing.JFileChooser;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

@NbBundle.Messages({
    "LBL_ExecutablePath=Executable Path:",
    "BTN_Browse=Browse...",
    "TITLE_SelectExecutable=Select OpenCode Executable"
})
public class OpenCodeOptionsPanel extends javax.swing.JPanel {

    private final OpenCodeOptionsPanelController controller;

    OpenCodeOptionsPanel(OpenCodeOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
    }

    private void initComponents() {
        jLabel1 = new javax.swing.JLabel();
        pathField = new javax.swing.JTextField();
        browseButton = new javax.swing.JButton();

        jLabel1.setText(NbBundle.getMessage(OpenCodeOptionsPanel.class, "LBL_ExecutablePath"));
        browseButton.setText(NbBundle.getMessage(OpenCodeOptionsPanel.class, "BTN_Browse"));
        browseButton.addActionListener(evt -> browseButtonActionPerformed());
        
        pathField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
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
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pathField, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(browseButton)
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
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
        chooser.setDialogTitle(NbBundle.getMessage(OpenCodeOptionsPanel.class, "TITLE_SelectExecutable"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            controller.changed();
        }
    }

    void load() {
        String defaultPath = System.getProperty("user.home") + "/.opencode/bin/opencode";
        pathField.setText(NbPreferences.forModule(OpenCodeOptionsPanel.class).get("opencodeExecutablePath", defaultPath));
    }

    void store() {
        NbPreferences.forModule(OpenCodeOptionsPanel.class).put("opencodeExecutablePath", pathField.getText());
    }

    boolean valid() {
        return true; // Simple validation for now
    }

    private javax.swing.JButton browseButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextField pathField;
}
