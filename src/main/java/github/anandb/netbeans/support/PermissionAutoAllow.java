package github.anandb.netbeans.support;

/**
 * Session-scoped "break mode" switch for permission requests.
 * <p>
 * While enabled, {@code ui/PermissionDialogManager} answers every incoming
 * {@code session/request_permission} with the request's allow-once option instead of showing
 * the prompt. The user confirms this once, when arming the mode, and it lasts until the button
 * is released or the IDE restarts.
 * <p>
 * Deliberately not a preference: silently auto-approving tool calls after a restart is exactly
 * the surprise this switch must not cause. Leaf static state, matching the {@code support/}
 * layer rules.
 */
public final class PermissionAutoAllow {

    private static volatile boolean enabled;

    private PermissionAutoAllow() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
