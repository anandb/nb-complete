package github.anandb.netbeans.ui;

import java.awt.Dimension;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import org.openide.util.NbBundle;

import github.anandb.netbeans.support.PluginSettings;

/**
 * Modal dialog for editing the session preamble text.
 * Replaces the inline textarea that was previously embedded in the options panel.
 */
final class PreambleDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final Dimension TEXT_SIZE = new Dimension(500, 300);

    private final JTextArea preambleArea;
    private boolean confirmed;

    PreambleDialog(Window owner, String currentText) {
        super(owner, NbBundle.getMessage(PreambleDialog.class, "TITLE_PreambleDialog"), Dialog.ModalityType.APPLICATION_MODAL);
        this.confirmed = false;

        preambleArea = new JTextArea(15, 50);
        preambleArea.setText(currentText);
        preambleArea.setLineWrap(true);
        preambleArea.setWrapStyleWord(true);
        preambleArea.setFont(preambleArea.getFont().deriveFont(13f));
        preambleArea.setComponentPopupMenu(createPopupMenu());

        JScrollPane scrollPane = new JScrollPane(preambleArea);
        scrollPane.setPreferredSize(TEXT_SIZE);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

        JButton resetButton = new JButton(NbBundle.getMessage(PreambleDialog.class, "LBL_PreambleReset"));
        resetButton.addActionListener(e -> preambleArea.setText(PluginSettings.getDefaultPreamble()));

        JButton clearButton = new JButton(NbBundle.getMessage(PreambleDialog.class, "LBL_PreambleClear"));
        clearButton.addActionListener(e -> preambleArea.setText(""));

        JButton cancelButton = new JButton(NbBundle.getMessage(PreambleDialog.class, "BTN_Cancel"));
        cancelButton.addActionListener(e -> dispose());

        JButton okButton = new JButton(NbBundle.getMessage(PreambleDialog.class, "BTN_Ok"));
        okButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        buttonPanel.add(resetButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        JLabel descLabel = new JLabel(NbBundle.getMessage(PreambleDialog.class, "LBL_PreambleDescription"));
        descLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        contentPanel.add(descLabel);

        contentPanel.add(scrollPane);
        contentPanel.add(buttonPanel);

        setContentPane(contentPanel);

        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
        setLocationRelativeTo(owner);
        setResizable(true);
    }

    boolean isConfirmed() { return confirmed; }

    String getPreambleText() { return preambleArea.getText(); }

    private JPopupMenu createPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem clearItem = new JMenuItem(
                NbBundle.getMessage(PreambleDialog.class, "LBL_PreambleClear"));
        clearItem.addActionListener(e -> preambleArea.setText(""));
        menu.add(clearItem);
        JMenuItem resetItem = new JMenuItem(
                NbBundle.getMessage(PreambleDialog.class, "LBL_PreambleReset"));
        resetItem.addActionListener(e -> preambleArea.setText(PluginSettings.getDefaultPreamble()));
        menu.add(resetItem);
        return menu;
    }
}
