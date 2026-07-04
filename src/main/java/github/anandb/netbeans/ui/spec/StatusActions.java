package github.anandb.netbeans.ui.spec;

/**
 * Callback contract for the status strip. Implemented by {@code AssistantTopComponent}
 * (or a lambda) and passed to {@link StatusSpec#build}. Default no-op methods
 * so partial implementations (e.g. a headless test harness) are valid.
 * <p>
 * <b>Swing-free</b>.
 */
public interface StatusActions {
    /** User clicked the status text (e.g. to copy / inspect). */
    default void onStatusClicked() {}
}
