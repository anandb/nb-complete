package github.anandb.netbeans.ui;

import java.awt.EventQueue;

import github.anandb.netbeans.support.PluginSettings;

/**
 * Routes "Send to Assistant" / "Ask Assistant" actions to either the Mini
 * Assistant dialog or the main assistant sidebar based on the
 * "Enable Mini-Assistant" preference.
 */
public final class AssistantTarget {

    private AssistantTarget() {
    }

    /** True when the Mini Assistant dialog is the configured target. */
    public static boolean isMini() {
        return PluginSettings.isMiniAssistantEnabled();
    }

    /**
     * Opens the active target and focuses its input. Never closes an already
     * visible sidebar — this is an "open" semantic, not a toggle.
     */
    public static void open() {
        if (isMini()) {
            MiniAssistantDialog dialog = MiniAssistantDialog.getInstance();
            if (dialog.isVisible()) {
                EventQueue.invokeLater(() -> {
                    dialog.toFront();
                    dialog.requestFocus();
                    dialog.getInputArea().requestFocusInWindow();
                });
            } else {
                dialog.toggleVisibility();
            }
        } else {
            AssistantTopComponent tc = openMainSidebar();
            if (tc != null) {
                tc.focusInput();
            }
        }
    }

    /** Opens the active target and prefills its input with the given text. */
    public static void showWithText(String text) {
        if (isMini()) {
            MiniAssistantDialog.getInstance().showWithText(text);
        } else {
            AssistantTopComponent tc = openMainSidebar();
            if (tc != null) {
                tc.setInputText(text);
            }
        }
    }

    /** Opens the main sidebar if closed and brings it to front. Returns the instance, or null. */
    private static AssistantTopComponent openMainSidebar() {
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc != null) {
            if (!tc.isOpened()) {
                tc.open();
            }
            tc.requestActive();
        }
        return tc;
    }
}
