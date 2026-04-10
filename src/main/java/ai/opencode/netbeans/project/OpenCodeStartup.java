package ai.opencode.netbeans.project;

import org.openide.modules.OnStart;

@OnStart
public class OpenCodeStartup implements Runnable {
    @Override
    public void run() {
        OpenCodeProjectManager.getInstance().start();
    }
}
