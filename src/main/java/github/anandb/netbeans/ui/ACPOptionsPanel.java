package github.anandb.netbeans.ui;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.manager.ModelCache;

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
    private JComboBox<String> modelCombo;
    private JTextField modelEditor;
    private List<String> allModels;

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
        modelCombo = new JComboBox<>();
        modelCombo.setEditable(true);
        modelEditor = (JTextField) modelCombo.getEditor().getEditorComponent();

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

        modelEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterModels();
                controller.changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterModels();
                controller.changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterModels();
                controller.changed();
            }
        });

        modelCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    String text = value.toString();
                    if (text.toLowerCase().contains("free")) {
                        setText(text);
                    }
                }
                return this;
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
                        .addComponent(modelCombo, 0, 400, Short.MAX_VALUE))
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
                    .addComponent(modelCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

        allModels = new ArrayList<>(ModelCache.getCachedModels());
        
        modelCombo.removeAllItems();
        for (String model : allModels) {
            modelCombo.addItem(model);
        }

        String savedModel = NbPreferences.forModule(ACPOptionsPanel.class).get("defaultModel", "acp/big-pickle");
        if (savedModel != null && !savedModel.isEmpty()) {
            if (!allModels.contains(savedModel)) {
                allModels.add(0, savedModel);
                modelCombo.insertItemAt(savedModel, 0);
            }
            modelCombo.setSelectedItem(savedModel);
        }

        preambleArea.setText(github.anandb.netbeans.manager.ACPSettings.getPreamble());
    }

    void store() {
        NbPreferences.forModule(ACPOptionsPanel.class).put("acpExecutablePath", pathField.getText());
        Object selected = modelCombo.getSelectedItem();
        String modelValue = selected != null ? selected.toString() : "";
        NbPreferences.forModule(ACPOptionsPanel.class).put("defaultModel", modelValue);
        if (modelValue != null && !modelValue.isEmpty()) {
            ModelCache.addModel(modelValue);
        }
        github.anandb.netbeans.manager.ACPSettings.setPreamble(preambleArea.getText());
    }

    private void filterModels() {
        SwingUtilities.invokeLater(() -> {
            String searchText = modelEditor.getText().toLowerCase();

            // Prevent recursion
            modelCombo.removeItemListener(e -> {});

            // Save selected item
            Object selected = modelCombo.getSelectedItem();

            // Filter models
            List<String> filtered = allModels.stream()
                    .filter(model -> model.toLowerCase().contains(searchText))
                    .collect(Collectors.toList());

            // Update model
            DefaultComboBoxModel<String> comboModel = new DefaultComboBoxModel<>();
            for (String model : filtered) {
                comboModel.addElement(model);
            }

            // Always show current text even if no match
            if (!searchText.isEmpty() && !filtered.contains(searchText)) {
                comboModel.insertElementAt(searchText, 0);
            }

            modelCombo.setModel(comboModel);

            // Restore selected item
            if (selected != null) {
                modelCombo.setSelectedItem(selected);
            }

            // Restore text
            modelEditor.setText(searchText);

            // Show popup if there are matches
            if (modelCombo.isDisplayable() && comboModel.getSize() > 0) {
                modelCombo.showPopup();
            }

            controller.changed();
        });
    }

    boolean valid() {
        return true;
    }
}
