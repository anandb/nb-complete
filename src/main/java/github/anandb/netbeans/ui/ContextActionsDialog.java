package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.openide.util.NbBundle;

/**
 * Compact editor for the set-and-forget context-menu toggles. Keeps the IDE
 * Extensions section to one summary row instead of one row per toggle; the
 * choices apply when the Options dialog is applied.
 */
final class ContextActionsDialog extends JDialog {

    /** One editable toggle: its copy and how to read/write the panel's checkbox. */
    record Toggle(String label, String tooltip, BooleanSupplier selected, Consumer<Boolean> setter) { }

    private final boolean[] selected;
    private boolean[] result;

    private ContextActionsDialog(Window owner, List<Toggle> toggles) {
        super(owner, NbBundle.getMessage(ACPOptionsPanel.class, "TITLE_ContextActions"),
                ModalityType.APPLICATION_MODAL);
        this.selected = new boolean[toggles.size()];
        setLayout(new BorderLayout(0, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel grid = new JPanel(new GridBagLayout());
        for (int i = 0; i < toggles.size(); i++) {
            final int index = i;
            Toggle toggle = toggles.get(i);
            boolean on = toggle.selected().getAsBoolean();
            selected[i] = on;
            JCheckBox box = new JCheckBox(toggle.label(), on);
            box.setToolTipText(toggle.tooltip());
            box.addActionListener(evt -> selected[index] = box.isSelected());
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = i % 2;
            c.gridy = i / 2;
            c.weightx = 1.0;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(0, i % 2 == 0 ? 0 : 12, 6, 0);
            grid.add(box, c);
        }
        add(grid, BorderLayout.CENTER);

        JButton ok = new JButton(NbBundle.getMessage(ACPOptionsPanel.class, "LBL_OK"));
        JButton cancel = new JButton(NbBundle.getMessage(ACPOptionsPanel.class, "BTN_Cancel"));
        ok.addActionListener(evt -> {
            result = selected.clone();
            dispose();
        });
        cancel.addActionListener(evt -> dispose());
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(evt -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JPanel.WHEN_IN_FOCUSED_WINDOW);

        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(ok);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Opens the dialog; a returned array aligns with {@code toggles}. */
    static Optional<boolean[]> show(Component parent, List<Toggle> toggles) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        ContextActionsDialog dialog = new ContextActionsDialog(owner, toggles);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }
}
