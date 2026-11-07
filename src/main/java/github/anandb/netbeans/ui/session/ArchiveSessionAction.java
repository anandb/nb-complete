package github.anandb.netbeans.ui.session;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import github.anandb.netbeans.contract.SessionControl;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import github.anandb.netbeans.ui.assistant.MiniAssistantDialog;
import github.anandb.netbeans.ui.assistant.ChatLayoutBuilder;
import github.anandb.netbeans.ui.platform.PlatformBridge;

@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.ArchiveSessionAction")
@ActionRegistration(displayName = "#CTL_ArchiveSessionAction")
public class ArchiveSessionAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        SessionControl sc =
            PlatformBridge.sessionControl();
        if (sc == null) return;
        String sid = sc.getCurrentSessionId();
        if (sid != null) {
            boolean currentlyHidden = sc.isHidden(sid);
            boolean newHidden = !currentlyHidden;
            sc.setHidden(sid, newHidden);
            sc.refreshSessions();
            if (newHidden && !ChatLayoutBuilder.isShowingHidden()) {
                MiniAssistantDialog.closeIfVisible();
            }
        }
    }
}
