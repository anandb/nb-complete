package github.anandb.netbeans.ui.options;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import github.anandb.netbeans.support.RunCommandPolicy;

import org.openide.util.NbBundle;
import github.anandb.netbeans.ui.UIBundle;

/**
 * Modal editor for the {@code run_command} allow/deny lists. Two text areas keep
 * the editor explicit without growing the System section of the options panel;
 * the values are returned to the panel and persisted only when Options is applied.
 */
final class RunCommandPolicyDialog extends JDialog {

    /** The edited lists. */
    record Result(String allowlist, String denylist) { }

    private final JTextArea allowArea = new JTextArea(8, 24);
    private final JTextArea denyArea = new JTextArea(8, 24);
    private final JLabel statusLabel = new JLabel();
    private Result result;

    private RunCommandPolicyDialog(Window owner, String allowlist, String denylist) {
        super(owner, NbBundle.getMessage(UIBundle.class, "TITLE_RunCommandPolicy"),
                ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(0, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel lists = new JPanel(new GridBagLayout());
        lists.add(new JLabel(NbBundle.getMessage(UIBundle.class, "LBL_DeniedBinaries")),
                gbc(0, 0, true, new Insets(0, 0, 4, 8)));
        lists.add(new JLabel(NbBundle.getMessage(UIBundle.class, "LBL_AllowedBinaries")),
                gbc(1, 0, true, new Insets(0, 0, 4, 0)));
        allowArea.setText(asLines(allowlist));
        denyArea.setText(asLines(denylist));
        lists.add(new JScrollPane(denyArea), gbc(0, 1, false, new Insets(0, 0, 0, 8)));
        lists.add(new JScrollPane(allowArea), gbc(1, 1, false, new Insets(0, 0, 0, 0)));
        add(lists, BorderLayout.CENTER);

        JLabel hint = new JLabel("<html><body style='width:420px'>"
                + NbBundle.getMessage(UIBundle.class, "HINT_RunCommandPolicy")
                + "</body></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        add(hint, BorderLayout.NORTH);

        JButton ok = new JButton(NbBundle.getMessage(UIBundle.class, "LBL_OK"));
        JButton cancel = new JButton(NbBundle.getMessage(UIBundle.class, "BTN_Cancel"));
        ok.addActionListener(evt -> {
            result = new Result(asSetting(allowArea.getText()), asSetting(denyArea.getText()));
            dispose();
        });
        cancel.addActionListener(evt -> dispose());
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(evt -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JPanel.WHEN_IN_FOCUSED_WINDOW);

        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(ok);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        DocumentListener statusUpdater = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateStatus();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateStatus();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateStatus();
            }
        };
        allowArea.getDocument().addDocumentListener(statusUpdater);
        denyArea.getDocument().addDocumentListener(statusUpdater);
        updateStatus();
        pack();
        setMinimumSize(new Dimension(getWidth(), getHeight()));
        setLocationRelativeTo(owner);
    }

    /** Explains the aggregate effect of the two lists at the bottom of the dialog. */
    private void updateStatus() {
        String allow = asSetting(allowArea.getText());
        String deny = asSetting(denyArea.getText());
        if (RunCommandPolicy.deniesAllCommands(allow, deny)) {
            statusLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_RunPolicyAllDenied"));
        } else if (RunCommandPolicy.allowsAllCommands(allow, deny)) {
            statusLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_RunPolicyAllAllowed"));
        } else {
            statusLabel.setText(" ");
        }
    }

    private static GridBagConstraints gbc(int x, int y, boolean label, Insets insets) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = insets;
        c.anchor = GridBagConstraints.NORTHWEST;
        if (!label) {
            c.weightx = 1.0;
            c.weighty = 1.0;
            c.fill = GridBagConstraints.BOTH;
        }
        return c;
    }

    private static String asLines(String setting) {
        if (setting == null || setting.isBlank()) {
            return "";
        }
        return Arrays.stream(setting.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private static String asSetting(String text) {
        List<String> names = Arrays.stream(text.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return String.join(", ", names);
    }

    /** Opens the dialog and returns the edited lists, or empty when cancelled. */
    static Optional<Result> show(Component parent, String allowlist, String denylist) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        RunCommandPolicyDialog dialog = new RunCommandPolicyDialog(owner, allowlist, denylist);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }
}
