package github.anandb.netbeans.project;

import github.anandb.netbeans.support.Logger;

import github.anandb.netbeans.manager.ProcessManager;
import github.anandb.netbeans.ui.ImagePasteTransferHandler;
import org.openide.modules.OnStop;

@OnStop
public class ACPShutdown implements Runnable {
    private static final Logger log = Logger.from(ACPShutdown.class);

    @Override
    public void run() {
        log.info("ACP Plugin Shutting down...");
        ProcessManager.getInstance().shutdown();
        ImagePasteTransferHandler.shutdownIoProcessor();
    }
}
