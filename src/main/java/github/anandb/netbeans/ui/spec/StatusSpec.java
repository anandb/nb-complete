package github.anandb.netbeans.ui.spec;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import github.anandb.netbeans.ui.ColorTheme;
import github.anandb.netbeans.ui.ThemeManager;
import github.anandb.netbeans.ui.vm.ChatToolbarVM;

/**
 * Pure builder for the status strip (status label + version + cwd).
 * <p>
 * This is the <b>Phase 7 pilot</b>: a self-contained spec that proves the
 * {@code ui/vm/} + {@code ui/spec/} seam works end-to-end. It is built in
 * imperative Swing but follows the exact shape a future swingtree version
 * would take — a {@code build(vm, actions)} method returning a refs record.
 * When the DSL lands, only this method body changes to:
 * <pre>{@code
 * return UI.of(panel("fillx, insets 4"))
 *     .add("growx", label(vm.statusText()))
 *     .add("right", label(vm.versionText()))
 *     .add("wrap, right", label(vm.cwdText()))
 *     .get(StatusRefs.class);
 * }</pre>
 * <p>
 * <b>Not wired into the live UI</b> (behavioral risk); this is a compiling
 * pilot. Wiring into {@code AssistantTopComponent} to replace the ad-hoc
 * status label construction is a follow-up PR.
 */
public final class StatusSpec {

    private StatusSpec() {}

    /**
     * Build the status strip and return its component refs.
     *
     * @param vm      the toolbar view-model carrying status/version/cwd text
     * @param actions the callback contract (status click)
     * @return immutable refs to the three labels + the panel
     */
    public static StatusRefsAndPanel build(ChatToolbarVM vm, StatusActions actions) {
        ColorTheme theme = ThemeManager.getCurrentTheme();

        JLabel statusLabel = new JLabel(vm.statusText(), SwingConstants.LEFT);
        statusLabel.setForeground(theme.mutedForeground());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

        JLabel versionLabel = new JLabel(vm.versionText(), SwingConstants.RIGHT);
        versionLabel.setForeground(theme.mutedForeground());

        JLabel cwdLabel = new JLabel(vm.cwdText(), SwingConstants.RIGHT);
        cwdLabel.setForeground(theme.mutedForeground());

        JPanel panel = new JPanel(new java.awt.BorderLayout(8, 0));
        panel.setOpaque(true);
        panel.setBackground(theme.sunkenBackground());
        panel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        panel.add(statusLabel, java.awt.BorderLayout.CENTER);
        panel.add(versionLabel, java.awt.BorderLayout.EAST);
        // cwd shown as tooltip to keep the strip compact
        panel.setToolTipText(vm.cwdText());

        // Wire the action (DSL-shaped: the listener delegates to the actions interface,
        // not to the top component directly).
        if (actions != null) {
            statusLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { actions.onStatusClicked(); }
            });
        }

        return new StatusRefsAndPanel(new StatusRefs(statusLabel, versionLabel, cwdLabel), panel);
    }

    /** Refs + the enclosing panel (the panel is needed by the caller for layout). */
    public record StatusRefsAndPanel(StatusRefs refs, JPanel panel) {}
}
