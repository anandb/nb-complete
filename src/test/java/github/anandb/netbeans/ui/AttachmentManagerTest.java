package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.AttachedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentManagerTest {

    @TempDir
    private Path tempDir;

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
    void buildFileBlocksImage() throws IOException {
        Path f = tempDir.resolve("test.png");
        Files.write(f, new byte[]{1, 2, 3});
        AttachmentManager am = new AttachmentManager();
        am.add(new AttachedFile(f.toFile()));
        List<Map<String, Object>> blocks = am.buildFileBlocks(true);
        assertEquals(1, blocks.size());
        Map<String, Object> block = blocks.get(0);
        assertEquals("image", block.get("type"));
        assertEquals("image/png", block.get("mimeType"));
        assertNotNull(block.get("data"));
    }

    @Test
    void buildFileBlocksEmbedsTextResource() throws IOException {
        Path f = tempDir.resolve("test.md");
        Files.writeString(f, "# Title\n\nBody\n");
        AttachmentManager am = new AttachmentManager();
        am.add(new AttachedFile(f.toFile()));
        List<Map<String, Object>> blocks = am.buildFileBlocks(true);
        assertEquals(1, blocks.size());
        Map<String, Object> block = blocks.get(0);
        assertEquals("resource", block.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) block.get("resource");
        assertEquals("text/markdown", resource.get("mimeType"));
        assertEquals("# Title\n\nBody\n", resource.get("text"));
        assertEquals(f.toFile().toURI().toString(), resource.get("uri"));
    }

    @Test
    void buildFileBlocksInlinesTextWithoutEmbeddedContext() throws IOException {
        Path f = tempDir.resolve("test.md");
        Files.writeString(f, "line one\n```\ncode\n```\n");
        AttachmentManager am = new AttachmentManager();
        am.add(new AttachedFile(f.toFile()));
        List<Map<String, Object>> blocks = am.buildFileBlocks(false);
        assertEquals(1, blocks.size());
        Map<String, Object> block = blocks.get(0);
        assertEquals("text", block.get("type"));
        // The inner triple-backtick fence must not close the block early, so the
        // wrapper fence grows to four backticks.
        assertEquals("Attached file `test.md`:\n````\nline one\n```\ncode\n```\n\n````\n", block.get("text"));
        // Inlined file bodies must not be replayed into the user bubble.
        assertEquals(Map.of("audience", List.of("assistant")), block.get("annotations"));
    }

    @Test
    void buildFileBlocksEmbedsBinaryAsBlob() throws IOException {
        Path f = tempDir.resolve("test.pdf");
        Files.write(f, new byte[]{1, 2, 3});
        AttachmentManager am = new AttachmentManager();
        am.add(new AttachedFile(f.toFile()));
        List<Map<String, Object>> blocks = am.buildFileBlocks(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) blocks.get(0).get("resource");
        assertEquals("application/pdf", resource.get("mimeType"));
        assertNotNull(resource.get("blob"));
        assertFalse(resource.containsKey("text"));
    }

    @Test
    void buildFileBlocksFallsBackToResourceLinkForBinary() throws IOException {
        Path f = tempDir.resolve("test.pdf");
        Files.write(f, new byte[]{1, 2, 3});
        AttachmentManager am = new AttachmentManager();
        am.add(new AttachedFile(f.toFile()));
        Map<String, Object> block = am.buildFileBlocks(false).get(0);
        assertEquals("resource_link", block.get("type"));
        assertEquals("test.pdf", block.get("name"));
        assertEquals(3L, block.get("size"));
        assertNotNull(block.get("uri"));
    }

    @Test
    void buildFileBlocksReturnsCopy() throws IOException {
        Path f = tempDir.resolve("test.txt");
        Files.writeString(f, "hello");
        AttachmentManager am = new AttachmentManager();
        am.add(new AttachedFile(f.toFile()));
        List<Map<String, Object>> blocks = am.buildFileBlocks(true);
        am.clear();
        assertFalse(blocks.isEmpty());
    }

    /**
     * A file beyond the inline budget must not enter the prompt whole: the block
     * carries a bounded body plus a marker, in both the embedded-resource and the
     * plain-text shape.
     */
    @Test
    void oversizedTextIsTruncatedWithAMarker() throws IOException {
        Path f = tempDir.resolve("huge.txt");
        Files.writeString(f, "x".repeat(AttachmentManager.MAX_INLINE_TEXT_CHARS + 5000));
        AttachmentManager am = new AttachmentManager();
        am.add(new AttachedFile(f.toFile()));

        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>)
                am.buildFileBlocks(true).get(0).get("resource");
        String embedded = (String) resource.get("text");
        assertEquals(AttachmentManager.MAX_INLINE_TEXT_CHARS, embedded.length());
        assertTrue(embedded.endsWith("continues]"), "embedded text carries the marker");

        String plain = (String) am.buildFileBlocks(false).get(0).get("text");
        assertTrue(plain.contains("[BeanBot: attachment truncated"), "inline block carries the marker");
        assertTrue(plain.length() < AttachmentManager.MAX_INLINE_TEXT_CHARS + 200,
                "inline block stays within the cap plus its wrapper");
    }

    @Test
    void addFromFilesSkipsOversized() throws IOException {
        Path small = tempDir.resolve("small.txt");
        Path big = tempDir.resolve("big.bin");
        Files.writeString(small, "small");
        byte[] data = new byte[11 * 1024 * 1024];
        Files.write(big, data);
        AttachmentManager am = new AttachmentManager();
        am.addFromFiles(new File[]{small.toFile(), big.toFile()});
        assertEquals(1, am.size());
    }

    @Test
    void addFromFilesSkipsNull() {
        AttachmentManager am = new AttachmentManager();
        am.addFromFiles(null);
        assertTrue(am.getAttachments().isEmpty());
    }
}
