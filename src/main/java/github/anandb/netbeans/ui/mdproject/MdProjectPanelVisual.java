package github.anandb.netbeans.ui.mdproject;

import java.io.File;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.regex.Pattern;
import org.openide.WizardDescriptor;
import org.openide.util.NbBundle;

/**
 * Visual panel for entering the project name and location.
 */
final class MdProjectPanelVisual extends JPanel {

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("\\w[\\w\\-.]*");

    private final javax.swing.JTextField nameField;
    private final javax.swing.JTextField folderField;
    private final javax.swing.JButton browseButton;
    private final javax.swing.JLabel errorLabel;
    private javax.swing.event.ChangeListener changeListener;

    MdProjectPanelVisual() {
        setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();

        c.insets = new java.awt.Insets(8, 8, 8, 8);
        c.anchor = java.awt.GridBagConstraints.WEST;

        // Project Name
        c.gridx = 0;
        c.gridy = 0;
        add(new javax.swing.JLabel(NbBundle.getMessage(MdProjectPanelVisual.class, "LBL_ProjectName")), c);

        c.gridx = 1;
        c.gridy = 0;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        nameField = new javax.swing.JTextField(30);
        add(nameField, c);

        // Project Location
        c.gridx = 0;
        c.gridy = 1;
        c.fill = java.awt.GridBagConstraints.NONE;
        c.weightx = 0;
        add(new javax.swing.JLabel(NbBundle.getMessage(MdProjectPanelVisual.class, "LBL_ProjectLocation")), c);

        c.gridx = 1;
        c.gridy = 1;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        folderField = new javax.swing.JTextField(30);
        folderField.setText(System.getProperty("user.home"));
        add(folderField, c);

        c.gridx = 2;
        c.gridy = 1;
        c.fill = java.awt.GridBagConstraints.NONE;
        c.weightx = 0;
        browseButton = new javax.swing.JButton(NbBundle.getMessage(MdProjectPanelVisual.class, "LBL_Browse"));
        add(browseButton, c);

        // Error label
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 2;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        errorLabel = new javax.swing.JLabel(" ");
        errorLabel.setForeground(java.awt.Color.RED);
        add(errorLabel, c);

        // Listeners
        DocumentListener dl = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { fireChange(); }
            @Override public void removeUpdate(DocumentEvent e) { fireChange(); }
            @Override public void changedUpdate(DocumentEvent e) { fireChange(); }
        };
        nameField.getDocument().addDocumentListener(dl);
        folderField.getDocument().addDocumentListener(dl);

        browseButton.addActionListener(e -> {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
            chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
            chooser.setCurrentDirectory(new File(folderField.getText()));
            if (chooser.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                folderField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
    }

    boolean valid() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            errorLabel.setText(NbBundle.getMessage(MdProjectPanelVisual.class, "ERR_EmptyName"));
            return false;
        }
        if (!VALID_NAME_PATTERN.matcher(name).matches()) {
            errorLabel.setText(NbBundle.getMessage(MdProjectPanelVisual.class, "ERR_InvalidName"));
            return false;
        }
        String folder = folderField.getText().trim();
        if (folder.isEmpty()) {
            errorLabel.setText(NbBundle.getMessage(MdProjectPanelVisual.class, "ERR_EmptyLocation"));
            return false;
        }
        File dir = new File(folder);
        if (!dir.isDirectory()) {
            errorLabel.setText(NbBundle.getMessage(MdProjectPanelVisual.class, "ERR_InvalidLocation"));
            return false;
        }
        File projectDir = new File(dir, name);
        if (projectDir.exists()) {
            errorLabel.setText(NbBundle.getMessage(MdProjectPanelVisual.class, "ERR_DirExists"));
            return false;
        }
        errorLabel.setText(" ");
        return true;
    }

    void read(WizardDescriptor wiz) {
        String name = (String) wiz.getProperty("projName");
        if (name != null) {
            nameField.setText(name);
        }
        String folder = (String) wiz.getProperty("projDir");
        if (folder != null) {
            folderField.setText(folder);
        }
    }

    void store(WizardDescriptor wiz) {
        wiz.putProperty("projName", nameField.getText().trim());
        wiz.putProperty("projDir", folderField.getText().trim());
    }

    void addChangeListener(ChangeListener l) {
        changeListener = l;
    }

    void removeChangeListener(ChangeListener l) {
        if (changeListener == l) {
            changeListener = null;
        }
    }

    private void fireChange() {
        if (changeListener != null) {
            changeListener.stateChanged(new javax.swing.event.ChangeEvent(this));
        }
    }
}
