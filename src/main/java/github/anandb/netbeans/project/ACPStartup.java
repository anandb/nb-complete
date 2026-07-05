package github.anandb.netbeans.project;

import org.openide.modules.OnStart;
import org.openide.util.NbPreferences;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;

import github.anandb.netbeans.contract.UpdateCheckerControl;
import github.anandb.netbeans.support.AgentUtils;
import github.anandb.netbeans.support.Logger;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;

@OnStart
public class ACPStartup implements Runnable {
    private static final Logger LOG = Logger.from(ACPStartup.class);

    @Override
    public void run() {
        LOG.info("ACP Plugin Startup: Initializing Project Manager...");
        ACPProjectManager.getInstance().start();
        checkVersionAndOpen();
        UpdateCheckerControl ucc = Lookup.getDefault().lookup(UpdateCheckerControl.class);
        if (ucc != null) {
            ucc.start();
        } else {
            LOG.warn("UpdateCheckerControl not found — update checks disabled");
        }
    }

    private void checkVersionAndOpen() {
        String currentVersion = AgentUtils.getVersion();
        String lastVersion = NbPreferences.forModule(ACPStartup.class).get("lastVersion", "");

        if (!currentVersion.equals(lastVersion)) {
            LOG.info("New version detected ({0}), opening Assistant sidebar (docked left)...", currentVersion);
            NbPreferences.forModule(ACPStartup.class).put("lastVersion", currentVersion);

            UpdateCheckerControl ucc = Lookup.getDefault().lookup(UpdateCheckerControl.class);
            if (ucc != null) {
                ucc.onInstallOrUpgrade();
            } else {
                LOG.warn("UpdateCheckerControl not found — install/upgrade schedule skipped");
            }

            WindowManager.getDefault().invokeWhenUIReady(() -> {
                TopComponent sidebar = WindowManager.getDefault().findTopComponent("AssistantTopComponent");
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

