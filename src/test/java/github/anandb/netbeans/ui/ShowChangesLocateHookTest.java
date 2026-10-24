package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Selection-to-file recovery for the Locate in System hook. Mercurial's
 * {@code SyncFileNode} exposes its file only through a public {@code getFile()},
 * so that accessor must be honoured alongside a plain Lookup entry.
 */
class ShowChangesLocateHookTest {

    @Test
    void fileComesFromTheNodeLookupFirst() {
        File file = new File("/tmp/from-lookup");
        Node node = new AbstractNode(Children.LEAF, Lookups.singleton(file));
        assertEquals(file, ShowChangesLocateHook.fileOf(node));
    }

    @Test
    void publicGetFileAccessorIsUsedWhenTheLookupHasNoFile() {
        File file = new File("/tmp/from-accessor");
        Node node = new AbstractNode(Children.LEAF, Lookup.EMPTY) {
            public File getFile() {
                return file;
            }
        };
        assertEquals(file, ShowChangesLocateHook.fileOf(node));
    }

    @Test
    void nodesWithoutAFileYieldNull() {
        Node node = new AbstractNode(Children.LEAF, Lookup.EMPTY);
        assertNull(ShowChangesLocateHook.fileOf(node));
    }
}
