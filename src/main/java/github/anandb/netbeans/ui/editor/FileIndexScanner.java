package github.anandb.netbeans.ui.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

import org.openide.util.RequestProcessor;

import github.anandb.netbeans.contract.FileCacheQuery;
import github.anandb.netbeans.pattern.FileNameMatcher;

/**
 * Runs the {@link FileNameMatcher} scan behind {@code GoToFileDialog} off the EDT.
 *
 * <p>A newer query supersedes an older one. The generation is checked while
 * scanning, again before publishing, and once more on the EDT, so a slow scan
 * can neither burn the whole index after the user typed on nor overwrite a
 * newer result list.</p>
 *
 * <p>Layer: ui feature helper — no Swing state, only {@link SwingUtilities} to
 * hand the finished list to the EDT.</p>
 */
final class FileIndexScanner {

    /** Checked every this many files so a superseded scan aborts promptly. */
    private static final int CANCEL_CHECK_MASK = 63;

    /** One finished scan. {@code hits} is empty when the query was unusable. */
    record ScanResult(long generation, String query, List<FileCacheQuery.CachedFile> hits,
            int indexedCount, boolean usable, long elapsedNanos) {
    }

    /** Pool for the scans; scans are short, so one worker keeps ordering simple. */
    private static final RequestProcessor POOL = new RequestProcessor("GoToFileSearch", 1);

    private final Executor executor;
    private final Consumer<ScanResult> publisher;
    private final AtomicLong generation = new AtomicLong();

    /**
     * @param executor runs the scan
     * @param publisher receives each surviving result on the EDT
     */
    FileIndexScanner(Executor executor, Consumer<ScanResult> publisher) {
        this.executor = executor;
        this.publisher = publisher;
    }

    /** Shared single-threaded scanner used by the dialog. */
    static FileIndexScanner withDefaultExecutor(Consumer<ScanResult> publisher) {
        return new FileIndexScanner(POOL, publisher);
    }

    /**
     * Starts a scan of {@code files} for {@code query}. A scan still running for
     * an older query is superseded and publishes nothing.
     */
    void search(String query, List<FileCacheQuery.CachedFile> files, int limit) {
        long gen = generation.incrementAndGet();
        executor.execute(() -> {
            ScanResult result = scan(gen, query.trim(), files, limit,
                    () -> gen != generation.get());
            if (result == null || gen != generation.get()) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (gen != generation.get()) {
                    return;
                }
                publisher.accept(result);
            });
        });
    }

    /**
     * Scans {@code files} on the calling thread, returning at most {@code limit}
     * matches in index order. Returns {@code null} when {@code cancelled} turned
     * true — the caller must then publish nothing.
     */
    static ScanResult scan(long gen, String queryText, List<FileCacheQuery.CachedFile> files,
            int limit, BooleanSupplier cancelled) {
        long startedAt = System.nanoTime();
        FileNameMatcher.Query query = FileNameMatcher.compile(queryText);
        List<FileCacheQuery.CachedFile> hits = new ArrayList<>();
        int indexed = files.size();
        if (!query.usable()) {
            return new ScanResult(gen, queryText, hits, indexed, false,
                    System.nanoTime() - startedAt);
        }
        int inspected = 0;
        for (FileCacheQuery.CachedFile cf : files) {
            if (hits.size() >= limit) {
                break;
            }
            if ((inspected++ & CANCEL_CHECK_MASK) == 0 && cancelled.getAsBoolean()) {
                return null;
            }
            if (query.matches(cf.fileObject().getNameExt(), cf.relativePath())) {
                hits.add(cf);
            }
        }
        return new ScanResult(gen, queryText, hits, indexed, true,
                System.nanoTime() - startedAt);
    }
}
