package github.anandb.netbeans.project;

import github.anandb.netbeans.manager.ACPManager;
import org.openide.modules.OnStop;

@OnStop
public class ACPShutdown implements Runnable {
    @Override
    public void run() {
        ACPManager.getInstance().shutdown();
    }
}
