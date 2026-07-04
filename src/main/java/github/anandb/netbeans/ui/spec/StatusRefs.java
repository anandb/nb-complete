package github.anandb.netbeans.ui.spec;

import javax.swing.JLabel;

/**
 * Immutable bundle of references to the status-strip components built by
 * {@link StatusSpec#build}. Mirrors the {@code statusLabel}/{@code versionLabel}/
 * {@code cwdLabel} fields today held on {@code AssistantTopComponent}.
 * <p>
 * <b>Not Swing-free</b> — UI-layer refs record.
 */
public record StatusRefs(
        JLabel statusLabel,
        JLabel versionLabel,
        JLabel cwdLabel
) {
}
