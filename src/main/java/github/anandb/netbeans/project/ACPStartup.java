package github.anandb.netbeans.project;

import org.openide.modules.OnStart;

@OnStart
public class ACPStartup implements Runnable {
    @Override
    public void run() {
        java.util.logging.Logger.getLogger(ACPStartup.class.getName()).info("ACP Plugin Startup: Initializing Project Manager...");
        ACPProjectManager.getInstance().start();
    }
}
