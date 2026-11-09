package github.anandb.netbeans.project.mdproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Conversion ("Open as Markdown Project"), the wizard and the {@link MdProjectFactory} all write
 * the marker through {@link MdProjectMarker}, so the name and the lost-race tolerance must be in
 * one place, and an existing marker must never be overwritten or, worse, deleted by a failed
 * conversion.
 */
class MdProjectMarkerTest {

    @Test
    void ensureCreatesTheMarker(@TempDir Path dir) throws Exception {
        FileObject folder = FileUtil.toFileObject(dir.toFile());
        assertNotNull(folder);

        FileObject marker = MdProjectMarker.ensure(folder);

        assertNotNull(marker);
        assertEquals(MdProjectMarker.NAME, marker.getNameExt());
        assertEquals(Collections.emptyList(), Collections.list(marker.getAttributes()),
                "the marker needs no attributes — attributes would drop a .nbattrs in the user's folder");
        assertTrue(Files.exists(dir.resolve(MdProjectMarker.NAME)));
    }

    @Test
    void ensureCreatingReportsWhatItDid(@TempDir Path dir) throws Exception {
        FileObject folder = FileUtil.toFileObject(dir.toFile());

        assertTrue(MdProjectMarker.ensureCreating(folder).created(),
                "a folder without a marker must report this call as the creator");
        assertFalse(MdProjectMarker.ensureCreating(folder).created(),
                "the second call only found the marker, it did not create it");
    }

    @Test
    void ensureIsIdempotentAndKeepsExistingContent(@TempDir Path dir) throws Exception {
        FileObject folder = FileUtil.toFileObject(dir.toFile());
        Path markerFile = dir.resolve(MdProjectMarker.NAME);
        Files.writeString(markerFile, "# notes the user added", StandardCharsets.UTF_8);
        folder.refresh();

        FileObject first = MdProjectMarker.ensure(folder);
        FileObject second = MdProjectMarker.ensure(folder);

        assertSame(first, second, "the existing marker is returned, not replaced");
        assertEquals("# notes the user added", Files.readString(markerFile, StandardCharsets.UTF_8));
    }

    @Test
    void aLostRaceReturnsTheMarkerTheOtherWriterCreated() throws Exception {
        FileObject raced = mock(FileObject.class);
        FileObject dir = mock(FileObject.class);
        // The concurrent writer wins between our check and our createData.
        when(dir.getFileObject(MdProjectMarker.NAME)).thenReturn(null, raced);
        when(dir.createData(MdProjectMarker.NAME)).thenThrow(new IOException("already exists"));

        assertFalse(MdProjectMarker.ensureCreating(dir).created(),
                "the losing call must not claim ownership of the marker");
        assertSame(raced, MdProjectMarker.ensure(dir));
    }

    @Test
    void anUnexplainedCreateFailureRethrows() throws Exception {
        FileObject dir = mock(FileObject.class);
        when(dir.getFileObject(MdProjectMarker.NAME)).thenReturn(null);
        when(dir.createData(MdProjectMarker.NAME)).thenThrow(new IOException("need the lock"));

        assertThrows(IOException.class, () -> MdProjectMarker.ensure(dir));
    }
}
