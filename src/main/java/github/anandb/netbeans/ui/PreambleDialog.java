package github.anandb.netbeans.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

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
    private final JButton copySystemButton;
    /** Shown above the copy button on hover; stays until the dialog closes. */
    private Popup copyTipPopup;
    private boolean confirmed;

    PreambleDialog(Window owner, String currentText) {
        super(owner, NbBundle.getMessage(PreambleDialog.class, "TITLE_PreambleDialog"), Dialog.ModalityType.APPLICATION_MODAL);
        this.confirmed = false;

        preambleArea = new JTextArea(15, 50);
        preambleArea.setText(currentText);

        JScrollPane scrollPane = new JScrollPane(preambleArea);
        scrollPane.setPreferredSize(TEXT_SIZE);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        copySystemButton = new JButton(NbBundle.getMessage(PreambleDialog.class, "LBL_PreambleCopySystem"));
        // Standard tooltip is dismissed too quickly for the long text; a
        // persistent popup above the button replaces it (hidden on dialog
        // close or mouse exit).
        copySystemButton.setToolTipText(null);
        copySystemButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { showCopyTooltip(); }
            @Override public void mouseExited(MouseEvent e) { hideCopyTooltip(); }
        });
        copySystemButton.addActionListener(e -> copyConsolidatedPrompt());

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

        buttonPanel.add(copySystemButton);
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
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { hideCopyTooltip(); }
            @Override public void windowClosing(WindowEvent e) { hideCopyTooltip(); }
        });

        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
        setLocationRelativeTo(owner);
        setResizable(true);
    }

    /** Shows the copy-button tooltip above the button. Plain Swing tooltips
     *  are dismissed by ToolTipManager before the long text can be read, so
     *  this is a manual popup that stays while the pointer is over the
     *  button and on dialog close. */
    private void showCopyTooltip() {
        if (copyTipPopup != null || !SwingUtilities.isEventDispatchThread()) {
            return;
        }
        String text = NbBundle.getMessage(PreambleDialog.class, "TT_PreambleCopySystem");
        StringBuilder html = new StringBuilder("<html><div style=\"width: 320px\">");
        for (String line : text.split("\n")) {
            if (html.length() > 40) {
                html.append("<br>");
            }
            html.append(line);
        }
        html.append("</div></html>");
        JLabel tip = new JLabel(html.toString());
        tip.setOpaque(true);
        tip.setBackground(UIManager.getColor("ToolTip.background"));
        tip.setForeground(UIManager.getColor("ToolTip.foreground"));
        javax.swing.border.Border border = UIManager.getBorder("ToolTip.border");
        tip.setBorder(border != null ? border : BorderFactory.createLineBorder(UIManager.getColor("ToolTip.foreground")));

        try {
            Point loc = copySystemButton.getLocationOnScreen();
            Dimension size = tip.getPreferredSize();
            copyTipPopup = PopupFactory.getSharedInstance().getPopup(copySystemButton, tip,
                    loc.x, loc.y - size.height - 4);
            copyTipPopup.show();
        } catch (java.awt.IllegalComponentStateException ex) {
            // Button not yet on screen — ignore hover before the dialog shows.
            copyTipPopup = null;
        }
    }

    private void hideCopyTooltip() {
        if (copyTipPopup != null) {
            copyTipPopup.hide();
            copyTipPopup = null;
        }
    }

    boolean isConfirmed() { return confirmed; }

    String getPreambleText() { return preambleArea.getText(); }

    /** Copies the consolidated startup prompt (critical rules + WSL guidance
     *  + the current preamble) to the clipboard, mirroring what sendPreamble
     *  injects via the user role. */
    private void copyConsolidatedPrompt() {
        String preamble = preambleArea.getText();
        StringBuilder combined = new StringBuilder();
        String rules = PluginSettings.getCriticalRules();
        if (rules != null && !rules.isBlank()) {
            combined.append(rules);
        }
        if (preamble != null && !preamble.isBlank()) {
            if (!combined.isEmpty()) {
                combined.append("\n\n");
            }
            combined.append(preamble);
        }
        StringSelection selection = new StringSelection(combined.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }


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
