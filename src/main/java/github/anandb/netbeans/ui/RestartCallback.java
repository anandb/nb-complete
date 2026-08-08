package github.anandb.netbeans.ui;

/**
 * Callback for the missing-binary bubble's restart action. Instead of a plain
 * {@link Runnable} (which would run unconditionally), the bubble passes a
 * {@code disableButtons} callback so the {@code onRestart} implementation can
 * gate it: the buttons are disabled only once a restart actually begins.
 */
@FunctionalInterface
interface RestartCallback {
    /**
     * Invoked when the user clicks Restart.
     *
     * @param disableButtons disables the bubble's action buttons; call it once
     *                       the restart actually starts (e.g. after the binary
     *                       is confirmed available)
     */
    void onRestart(Runnable disableButtons);
}
