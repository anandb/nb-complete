package github.anandb.netbeans.ui.session;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import github.anandb.netbeans.contract.SessionControl;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import github.anandb.netbeans.ui.platform.PlatformBridge;

@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.ArchiveSessionAction")
@ActionRegistration(displayName = "#CTL_ArchiveSessionAction")
public class ArchiveSessionAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        SessionControl sc = PlatformBridge.sessionControl();
        if (sc == null) {
            return;
        }
        SessionArchive.toggle(sc, sc.getCurrentSessionId());
    }
}
