package github.anandb.netbeans.project;

import java.util.logging.Logger;

import github.anandb.netbeans.manager.ACPManager;
import org.openide.modules.OnStop;

@OnStop
public class ACPShutdown implements Runnable {
    private static final Logger log = Logger.getLogger(ACPShutdown.class.getName());

    @Override
    public void run() {
        log.info("ACP Plugin Shutting down...");
        ACPManager.getInstance().shutdown();
    }
}
