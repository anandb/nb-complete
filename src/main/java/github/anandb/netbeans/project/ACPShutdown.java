package github.anandb.netbeans.project;

import github.anandb.netbeans.support.Logger;

import github.anandb.netbeans.contract.FileCacheQuery;
import github.anandb.netbeans.contract.ModelListControl;
import github.anandb.netbeans.contract.PinnedMessageControl;
import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.UsageStatsStore;
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
        flushStores();
        FileCacheQuery cache = Lookup.getDefault().lookup(FileCacheQuery.class);
        if (cache != null) {
            cache.dispose();
        }
        ImagePasteIoProcessor.shutdown();
    }

    /**
     * Writes coalesced stores now, then closes the database. The close is what
     * releases the H2 file lock: an in-place plugin upgrade loads the new module
     * in the same JVM, and its H2 cannot lock a file the old classloader still
     * holds. Without it the upgraded plugin reports "Database may be already in
     * use" and loses capture for the session.
     */
    private void flushStores() {
        PinnedMessageControl pins = Lookup.getDefault().lookup(PinnedMessageControl.class);
        if (pins != null) {
            pins.flush();
        }
        ModelListControl models = Lookup.getDefault().lookup(ModelListControl.class);
        if (models != null) {
            models.flush();
        }
        UsageStatsStore store = Lookup.getDefault().lookup(UsageStatsStore.class);
        if (store != null) {
            store.close();
        }
    }
}
