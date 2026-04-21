package github.anandb.netbeans.project;

import java.util.logging.Logger;

import org.openide.modules.OnStart;

@OnStart
public class ACPStartup implements Runnable {
    private static final Logger log = Logger.getLogger(ACPStartup.class.getName());

    @Override
    public void run() {
        log.info("ACP Plugin Startup: Initializing Project Manager...");
        ACPProjectManager.getInstance().start();
    }
}
