package github.anandb.netbeans.ui.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import github.anandb.netbeans.contract.FileCacheQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The jump-to-file scan runs off the EDT: a newer query must win, the result
 * cap must hold, and only the surviving filtered list may reach the EDT.
 */
class FileIndexScannerTest {

    /** Runs submitted scans only when the test says so. */
    private static final class ManualExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void run(int index) {
            tasks.get(index).run();
        }

        int size() {
            return tasks.size();
        }
    }

    @Test
    void newerQuerySupersedesAnOlderScan() throws Exception {
        ManualExecutor executor = new ManualExecutor();
        List<FileIndexScanner.ScanResult> published = Collections.synchronizedList(new ArrayList<>());
        List<Boolean> publishedOnEdt = Collections.synchronizedList(new ArrayList<>());
        FileIndexScanner scanner = new FileIndexScanner(executor, result -> {
            publishedOnEdt.add(SwingUtilities.isEventDispatchThread());
            published.add(result);
        });

        List<FileCacheQuery.CachedFile> files = files("AlphaOne.java", "BetaOne.java");

        scanner.search("Alpha", files, 200);
        scanner.search("Beta", files, 200);
        assertEquals(2, executor.size(), "each query must submit exactly one scan");

        // The superseded scan finishes last: it must publish nothing.
        executor.run(0);
        flushEdt();
        assertTrue(published.isEmpty(), "an older scan overwrote the newer query");

        executor.run(1);
        flushEdt();

        assertEquals(1, published.size(), "only the newest scan may reach the EDT");
        FileIndexScanner.ScanResult result = published.get(0);
        assertEquals("Beta", result.query());
        assertEquals(List.of("BetaOne.java"), names(result));
        assertTrue(publishedOnEdt.get(0), "results must be published on the EDT");
    }

    @Test
    void cancelledScanReturnsNothing() {
        List<FileCacheQuery.CachedFile> files = files("AlphaOne.java", "AlphaTwo.java");
        assertNull(FileIndexScanner.scan(1, "Alpha", files, 200, () -> true),
                "a superseded scan must abort instead of finishing");
    }

    @Test
    void resultsRespectTheMatchCap() {
        List<FileCacheQuery.CachedFile> files = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            files.add(entry(String.format("File%03d.java", i)));
        }

        FileIndexScanner.ScanResult result = FileIndexScanner.scan(1, "File", files, 200, () -> false);

        assertEquals(200, result.hits().size(), "the 200-match cap must hold");
        assertEquals(500, result.indexedCount());
        assertTrue(result.usable());
    }

    @Test
    void unusableQueryIsReportedWithoutHits() {
        FileIndexScanner.ScanResult shortQuery =
                FileIndexScanner.scan(1, "ab", files("abc.java"), 200, () -> false);

        assertFalse(shortQuery.usable(), "a query below the minimum must be flagged");
        assertTrue(shortQuery.hits().isEmpty());
    }

    private static List<String> names(FileIndexScanner.ScanResult result) {
        List<String> names = new ArrayList<>();
        for (FileCacheQuery.CachedFile cf : result.hits()) {
            names.add(cf.fileObject().getNameExt());
        }
        return names;
    }

    private static List<FileCacheQuery.CachedFile> files(String... names) {
        List<FileCacheQuery.CachedFile> files = new ArrayList<>();
        for (String name : names) {
            files.add(entry(name));
        }
        return files;
    }

    private static FileCacheQuery.CachedFile entry(String name) {
        try {
            FileObject fo = FileUtil.createMemoryFileSystem().getRoot().createData(name);
            return new FileCacheQuery.CachedFile(fo, "proj", "src/" + name);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
