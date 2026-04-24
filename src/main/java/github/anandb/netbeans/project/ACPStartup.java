package github.anandb.netbeans.project;

import org.openide.modules.OnStart;
import org.openide.util.NbPreferences;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;

import github.anandb.netbeans.manager.AgentUtils;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.ui.AssistantTopComponent;

@OnStart
public class ACPStartup implements Runnable {
    private static final Logger LOG = new Logger(ACPStartup.class);

    @Override
    public void run() {
        LOG.info("ACP Plugin Startup: Initializing Project Manager...");
        ACPProjectManager.getInstance().start();
        
        checkVersionAndOpen();
    }

    private void checkVersionAndOpen() {
        String currentVersion = AgentUtils.getVersion();
        String lastVersion = NbPreferences.forModule(ACPStartup.class).get("lastVersion", "");

        if (!currentVersion.equals(lastVersion)) {
            LOG.info("New version detected ({0}), opening Assistant sidebar (docked left)...", currentVersion);
            NbPreferences.forModule(ACPStartup.class).put("lastVersion", currentVersion);
            
            WindowManager.getDefault().invokeWhenUIReady(() -> {
                AssistantTopComponent sidebar = AssistantTopComponent.findInstance();
                if (sidebar != null) {
                    Mode explorer = WindowManager.getDefault().findMode("explorer");
                    if (explorer != null) {
                        explorer.dockInto(sidebar);
                    }
                    sidebar.open();
                    sidebar.requestActive();
                }
            });
        }
    }
}
