package github.anandb.netbeans.ui;

import java.io.File;
import javax.swing.JFileChooser;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

@NbBundle.Messages({
    "LBL_ExecutablePath=Executable Path:",
    "LBL_DefaultModel=Default Model:",
    "BTN_Browse=Browse...",
    "TITLE_SelectExecutable=Select Assistant Executable",
    "LBL_Preamble=Session preamble (sent as first user message for new chats):"
})
public class ACPOptionsPanel extends javax.swing.JPanel {

    private final ACPOptionsPanelController controller;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextField pathField;
    private javax.swing.JLabel preambleLabel;
    private javax.swing.JTextArea preambleArea;
    private javax.swing.JScrollPane preambleScroll;
    private javax.swing.JButton browseButton;
    private javax.swing.JLabel modelLabel;
    private javax.swing.JTextField modelField;

    ACPOptionsPanel(ACPOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
    }

    private void initComponents() {
        jLabel1 = new javax.swing.JLabel();
        pathField = new javax.swing.JTextField();
        preambleLabel = new javax.swing.JLabel();
        preambleArea = new javax.swing.JTextArea(5, 40);
        preambleScroll = new javax.swing.JScrollPane(preambleArea);
        browseButton = new javax.swing.JButton();
        modelLabel = new javax.swing.JLabel();
        modelField = new javax.swing.JTextField();

        jLabel1.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_ExecutablePath"));
        modelLabel.setText(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_DefaultModel"));
        browseButton.setText(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Browse"));
        browseButton.addActionListener(evt -> browseButtonActionPerformed());
        
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
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                controller.changed();
            }
        });

        modelField.addKeyListener(new java.awt.event.KeyAdapter() {
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
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pathField, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(browseButton))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(modelLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(modelField, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE))
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
                    .addComponent(modelLabel)
                    .addComponent(modelField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(preambleLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(preambleScroll, javax.swing.GroupLayout.DEFAULT_SIZE, 120, Short.MAX_VALUE)
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

    void load() {
        String defaultPath = System.getProperty("user.home") + "/.opencode/bin/opencode";
        pathField.setText(NbPreferences.forModule(ACPOptionsPanel.class).get("acpExecutablePath", defaultPath));
        modelField.setText(NbPreferences.forModule(ACPOptionsPanel.class).get("defaultModel", "acp/big-pickle"));
        preambleArea.setText(github.anandb.netbeans.manager.ACPSettings.getPreamble());
    }

    void store() {
        NbPreferences.forModule(ACPOptionsPanel.class).put("acpExecutablePath", pathField.getText());
        NbPreferences.forModule(ACPOptionsPanel.class).put("defaultModel", modelField.getText());
        github.anandb.netbeans.manager.ACPSettings.setPreamble(preambleArea.getText());
    }

    boolean valid() {
        return true; // Simple validation for now
    }

    
}
