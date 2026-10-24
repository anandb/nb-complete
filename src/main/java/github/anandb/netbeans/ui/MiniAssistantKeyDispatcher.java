package github.anandb.netbeans.ui;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;

import org.openide.util.Lookup;
import org.openide.util.Utilities;

import github.anandb.netbeans.contract.ProcessControl;

/**
 * Global key dispatcher installed by {@link MiniAssistantDialog}. Handles bubble
 * navigation, escape/enter/tab behaviour and the assistant shortcuts while the dialog is
 * showing and owns focus.
 */
final class MiniAssistantKeyDispatcher implements KeyEventDispatcher {

    private final MiniAssistantDialog dialog;

    MiniAssistantKeyDispatcher(MiniAssistantDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!dialog.isVisible()) return false;
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner != dialog && !dialog.isAncestorOf(focusOwner)) return false;

        if (e.getID() != KeyEvent.KEY_PRESSED) {
            return false;
        }

        PlaceholderTextArea inputArea = dialog.inputArea();
        AutocompleteManager autocompleteManager = dialog.autocompleteManager();

        boolean isMac = Utilities.isMac();
        boolean isCmdOrCtrl = isMac ? e.isMetaDown() : e.isControlDown();
        boolean isPrev = e.getKeyCode() == KeyEvent.VK_PAGE_UP ||
                         (isMac && e.getKeyCode() == KeyEvent.VK_LEFT && e.isMetaDown());
        boolean isNext = e.getKeyCode() == KeyEvent.VK_PAGE_DOWN ||
                         (isMac && e.getKeyCode() == KeyEvent.VK_RIGHT && e.isMetaDown());

        if (isPrev) {
            dialog.navigateAssistantBubble(-1);
            return true;
        } else if (isNext) {
            dialog.navigateAssistantBubble(1);
            return true;
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (autocompleteManager != null && autocompleteManager.isPopupVisible()) {
                autocompleteManager.handleKeyReleased(e);
                return true;
            }
            AssistantTopComponent tc = AssistantTopComponent.findInstance();
            if (tc != null && tc.getConfigPanelController() != null
                    && tc.getConfigPanelController().isAnyPopupVisible()) {
                tc.getConfigPanelController().closeAnyPopup();
                return true;
            }
            dialog.setVisible(false);
            return true;
        } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (inputArea.isFocusOwner()) {
                if (autocompleteManager != null && autocompleteManager.isPopupVisible()) {
                    autocompleteManager.handleKeyPressed(e);
                    return true;
                }
                if (e.isShiftDown()) {
                    inputArea.insert("\n", inputArea.getCaretPosition());
                    return true;
                } else {
                    dialog.sendMessage();
                    return true;
                }
            }
        } else if (e.getKeyCode() == KeyEvent.VK_TAB) {
            if (inputArea.isFocusOwner()) {
                e.consume();
                if (autocompleteManager != null && autocompleteManager.isPopupVisible()) {
                    autocompleteManager.selectCommand();
                } else {
                    var pc = Lookup.getDefault().lookup(ProcessControl.class);
                    var interceptor = pc != null ? pc.getSlashCommandInterceptor() : null;
                    var cb = interceptor != null ? interceptor.getCallback() : null;
                    if (cb != null) {
                        cb.popupAgentCombo();
                    }
                }
                return true;
            }
        } else if (e.getKeyCode() == KeyEvent.VK_C && isCmdOrCtrl) {
            dialog.copyContent();
            return true;
        } else if (e.getKeyCode() == KeyEvent.VK_L && isCmdOrCtrl && !e.isAltDown()) {
            AssistantTopComponent tc = AssistantTopComponent.findInstance();
            if (tc != null) {
                tc.toggleVisibility();
            }
            return true;
        } else if (e.getKeyCode() == KeyEvent.VK_L && isCmdOrCtrl && e.isAltDown()) {
            dialog.toggleVisibility();
            return true;
        }
        return false;
    }
}
