package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionReference;
import org.openide.util.Lookup;

@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.ArchiveSessionAction")
@ActionRegistration(displayName = "#CTL_ArchiveSessionAction")
@ActionReference(path = "Actions/Assistant", position = 400)
public class ArchiveSessionAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        github.anandb.netbeans.contract.SessionControl sc =
            Lookup.getDefault().lookup(github.anandb.netbeans.contract.SessionControl.class);
        if (sc == null) return;
        String sid = sc.getCurrentSessionId();
        if (sid != null) {
            boolean currentlyHidden = sc.isHidden(sid);
            sc.setHidden(sid, !currentlyHidden);
            sc.refreshSessions();
        }
    }
}
