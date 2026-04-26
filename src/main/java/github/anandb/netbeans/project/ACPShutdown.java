package github.anandb.netbeans.project;

import github.anandb.netbeans.support.Logger;

import github.anandb.netbeans.manager.ACPManager;
import org.openide.modules.OnStop;

@OnStop
public class ACPShutdown implements Runnable {
    private static final Logger log = new Logger(ACPShutdown.class);

    @Override
    public void run() {
        log.info("ACP Plugin Shutting down...");
        ACPManager.getInstance().shutdown();
    }
}
