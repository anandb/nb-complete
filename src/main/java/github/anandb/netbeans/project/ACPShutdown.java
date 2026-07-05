package github.anandb.netbeans.project;

import github.anandb.netbeans.support.Logger;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.support.ImagePasteIoProcessor;
import org.openide.modules.OnStop;
import org.openide.util.Lookup;

@OnStop
public class ACPShutdown implements Runnable {
    private static final Logger log = Logger.from(ACPShutdown.class);

    @Override
    public void run() {
        log.info("ACP Plugin Shutting down...");
        ProcessControl pc = Lookup.getDefault().lookup(ProcessControl.class);
        if (pc != null) {
            pc.shutdown();
        }
        ImagePasteIoProcessor.shutdown();
    }
}
