package github.anandb.netbeans.manager;

import org.openide.modules.OnStart;

/**
 * Eagerly initializes the {@link FileCacheManager} at plugin startup
 * so the file index is building while projects load — by the time
 * the user presses Ctrl+N the cache is ready.
 */
@OnStart
public class FileCacheInitializer implements Runnable {

    @Override
    public void run() {
        FileCacheManager.getDefault();
    }
}
