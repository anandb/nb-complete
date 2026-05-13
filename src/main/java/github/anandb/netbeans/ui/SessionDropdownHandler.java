package github.anandb.netbeans.ui;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import github.anandb.netbeans.manager.SessionManager;
import github.anandb.netbeans.model.SessionItem;

/**
 * Handles session dropdown popup behavior: tracking session selection changes
 * and restoring focus to the input area after popup interactions.
 */
public class SessionDropdownHandler {

    private final PlaceholderTextArea inputArea;

    public SessionDropdownHandler(JComboBox<SessionItem> sessionDropdown, PlaceholderTextArea inputArea) {
        this.inputArea = inputArea;
        installListeners(sessionDropdown);
    }

    private void installListeners(JComboBox<SessionItem> sessionDropdown) {
        sessionDropdown.addPopupMenuListener(new PopupMenuListener() {
            private String prePopupSessionId;

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                SessionItem selected = (SessionItem) sessionDropdown.getSelectedItem();
                prePopupSessionId = selected != null ? selected.getSession().id() : null;
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                SessionItem selected = (SessionItem) sessionDropdown.getSelectedItem();
                String currentId = selected != null ? selected.getSession().id() : null;
                if (currentId != null && !currentId.equals(prePopupSessionId)) {
                    SessionManager.getInstance().loadSession(currentId);
                }
                SwingUtilities.invokeLater(() -> inputArea.requestFocusInWindow());
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                SwingUtilities.invokeLater(() -> inputArea.requestFocusInWindow());
            }
        });
    }
}
