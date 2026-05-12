package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.AttachedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void initiallyEmpty() {
        AttachmentManager am = new AttachmentManager();
        assertTrue(am.getAttachments().isEmpty());
        assertEquals(0, am.size());
    }

    @Test
    void canAddUnderLimit() {
        AttachmentManager am = new AttachmentManager();
        assertTrue(am.canAdd());
    }

    @Test
    void addValidFile() throws IOException {
        Path f = tempDir.resolve("test.txt");
        Files.writeString(f, "hello");
        AttachmentManager am = new AttachmentManager();
        assertTrue(am.add(new AttachedFile(f.toFile())));
        assertEquals(1, am.size());
    }

    @Test
    void rejectOversizedFile() throws IOException {
        Path f = tempDir.resolve("big.bin");
        byte[] data = new byte[11 * 1024 * 1024];
        Files.write(f, data);
        AttachmentManager am = new AttachmentManager();
        assertFalse(am.add(new AttachedFile(f.toFile())));
        assertEquals(0, am.size());
    }

    @Test
    void rejectMoreThanMax() throws IOException {
        Path f1 = tempDir.resolve("f1.txt");
        Path f2 = tempDir.resolve("f2.txt");
        Path f3 = tempDir.resolve("f3.txt");
        Files.writeString(f1, "a");
        Files.writeString(f2, "b");
        Files.writeString(f3, "c");
        AttachmentManager am = new AttachmentManager();
        assertTrue(am.add(new AttachedFile(f1.toFile())));
        assertTrue(am.add(new AttachedFile(f2.toFile())));
        assertFalse(am.add(new AttachedFile(f3.toFile())));
        assertEquals(2, am.size());
    }

    @Test
    void removeFile() throws IOException {
        Path f = tempDir.resolve("test.txt");
        Files.writeString(f, "hello");
        AttachmentManager am = new AttachmentManager();
        AttachedFile af = new AttachedFile(f.toFile());
        am.add(af);
        assertTrue(am.remove(af));
        assertTrue(am.getAttachments().isEmpty());
    }

    @Test
    void clearRemovesAll() throws IOException {
        Path f1 = tempDir.resolve("f1.txt");
        Files.writeString(f1, "a");
        AttachmentManager am = new AttachmentManager();
        am.add(new AttachedFile(f1.toFile()));
        am.clear();
        assertTrue(am.getAttachments().isEmpty());
    }

    @Test
    void buildFileBlocks() throws IOException {
        Path f = tempDir.resolve("test.png");
        Files.write(f, new byte[]{1, 2, 3});
        AttachmentManager am = new AttachmentManager();
        am.add(new AttachedFile(f.toFile()));
        List<Map<String, Object>> blocks = am.buildFileBlocks();
        assertEquals(1, blocks.size());
        Map<String, Object> block = blocks.get(0);
        assertEquals("image", block.get("type"));
        assertEquals("test.png", block.get("filename"));
        assertEquals("image/png", block.get("mimeType"));
        assertNotNull(block.get("data"));
    }

    @Test
    void buildFileBlocksReturnsCopy() throws IOException {
        Path f = tempDir.resolve("test.txt");
        Files.writeString(f, "hello");
        AttachmentManager am = new AttachmentManager();
        am.add(new AttachedFile(f.toFile()));
        List<Map<String, Object>> blocks = am.buildFileBlocks();
        am.clear();
        assertFalse(blocks.isEmpty());
    }

    @Test
    void addFromFilesSkipsOversized() throws IOException {
        Path small = tempDir.resolve("small.txt");
        Path big = tempDir.resolve("big.bin");
        Files.writeString(small, "small");
        byte[] data = new byte[11 * 1024 * 1024];
        Files.write(big, data);
        AttachmentManager am = new AttachmentManager();
        am.addFromFiles(new java.io.File[]{small.toFile(), big.toFile()});
        assertEquals(1, am.size());
    }

    @Test
    void addFromFilesSkipsNull() {
        AttachmentManager am = new AttachmentManager();
        am.addFromFiles(null);
        assertTrue(am.getAttachments().isEmpty());
    }
}
