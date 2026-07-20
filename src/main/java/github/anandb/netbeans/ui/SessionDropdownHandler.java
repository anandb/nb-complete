package github.anandb.netbeans.ui;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import github.anandb.netbeans.model.SessionItem;
import org.openide.util.Lookup;

import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.SessionService;

/**
 * Handles session dropdown popup behavior: tracking session selection changes
 * and restoring focus to the input area after popup interactions.
 */
// DSL-CONTROLLER: not a view — dropdown popup visibility / hide-on-outside-click
// state. Stays imperative; the JComboBox it drives is bound by ChatLayoutSpec.
public class SessionDropdownHandler {

    private final SessionService sessionService = Lookup.getDefault().lookup(PlatformBridge.class).sessionService();

    private final PlaceholderTextArea inputArea;

    public SessionDropdownHandler(JComboBox<SessionItem> sessionDropdown, PlaceholderTextArea inputArea) {
        this.inputArea = inputArea;
        installListeners(sessionDropdown);
    }

    private void installListeners(JComboBox<SessionItem> sessionDropdown) {
        final Object[] prePopupSession = {null};
        final long[] lastSessionSwitch = {0L};

        sessionDropdown.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                prePopupSession[0] = sessionDropdown.getSelectedItem();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                long now = System.currentTimeMillis();
                SessionItem selected = (SessionItem) sessionDropdown.getSelectedItem();
                String currentId = selected != null ? selected.getSession().id() : null;
                String previousId = prePopupSession[0] != null ? ((SessionItem)prePopupSession[0]).getSession().id() : null;
                if (currentId != null && previousId != null && !currentId.equals(previousId)) {
                    if (now - lastSessionSwitch[0] < 500) return;
                    lastSessionSwitch[0] = now;
                    boolean success = sessionService.get().loadSession(currentId);
                    if (!success) {
                        github.anandb.netbeans.support.Logger.from(SessionDropdownHandler.class)
                                .warn("Failed to load session {0} from dropdown", currentId);
                        SwingUtilities.invokeLater(() -> sessionDropdown.setSelectedItem(prePopupSession[0]));
                    }
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
