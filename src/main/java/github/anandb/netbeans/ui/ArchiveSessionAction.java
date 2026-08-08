package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import github.anandb.netbeans.contract.SessionControl;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.util.Lookup;

@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.ArchiveSessionAction")
@ActionRegistration(displayName = "#CTL_ArchiveSessionAction")
public class ArchiveSessionAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        SessionControl sc =
            Lookup.getDefault().lookup(SessionControl.class);
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
