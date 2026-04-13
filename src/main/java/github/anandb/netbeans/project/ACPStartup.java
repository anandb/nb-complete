package github.anandb.netbeans.project;

import org.openide.modules.OnStart;

@OnStart
public class ACPStartup implements Runnable {
    @Override
    public void run() {
        ACPProjectManager.getInstance().start();
    }
}
