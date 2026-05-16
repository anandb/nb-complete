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
        final Object[] prePopupSession = {null};

        sessionDropdown.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                prePopupSession[0] = sessionDropdown.getSelectedItem();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                SessionItem selected = (SessionItem) sessionDropdown.getSelectedItem();
                String currentId = selected != null ? selected.getSession().id() : null;
                String previousId = prePopupSession[0] != null ? ((SessionItem)prePopupSession[0]).getSession().id() : null;
                if (currentId != null && previousId != null && !currentId.equals(previousId)) {
                    SessionManager.getInstance().loadSession(currentId);
                }
                SwingUtilities.invokeLater(() -> inputArea.requestFocusInWindow());
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                if (prePopupSession[0] != null) {
                    sessionDropdown.setSelectedItem(prePopupSession[0]);
                }
                SwingUtilities.invokeLater(() -> inputArea.requestFocusInWindow());
            }
        });
    }
}
