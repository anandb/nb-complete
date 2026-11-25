package github.anandb.netbeans.ui.composer;

import github.anandb.netbeans.model.AttachedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentButtonTest {

    @TempDir
    private Path tempDir;

    /** Records which outcome notifications the button fired. */
    private static final class RecordingCallback implements PasteCallback {
        final List<String> events = new ArrayList<>();

        @Override
        public boolean canAddAttachment() {
            return true; // AttachmentButton owns the cap check before notifying
        }

        @Override
        public void onAttachmentAdded(AttachedFile file) {
            events.add("added:" + file.filename());
        }

        @Override
        public void onError(String message) {
            events.add("error");
        }

        @Override
        public void onAttachmentLimitReached() {
            events.add("limit");
        }

        @Override
        public void onFilesAttached(List<AttachedFile> added) {
            events.add("filesAttached:" + added.size());
        }

        @Override
        public void onAttachmentTooLarge() {
            events.add("tooLarge");
        }
    }

    private static AttachedFile file(String name, long size) {
        return new AttachedFile(name, "text/plain", new byte[0], size, "file:///" + name);
    }

    @Test
    void attachFileAddsAndNotifies() {
        RecordingCallback callback = new RecordingCallback();
        AttachmentManager manager = new AttachmentManager();
        AttachmentButton button = new AttachmentButton(manager, callback, null);

        assertTrue(button.attachFile(file("a.txt", 10)));
        assertEquals(1, manager.size());
        assertEquals(List.of("filesAttached:1"), callback.events);
    }

    @Test
    void attachFileRejectsOversizeWithTooLargeNotification() {
        RecordingCallback callback = new RecordingCallback();
        AttachmentButton button = new AttachmentButton(new AttachmentManager(), callback, null);

        assertFalse(button.attachFile(file("big.txt", Long.MAX_VALUE)));
        assertEquals(List.of("tooLarge"), callback.events);
    }

    @Test
    void attachFileHonorsCountCap() {
        RecordingCallback callback = new RecordingCallback();
        AttachmentButton button = new AttachmentButton(new AttachmentManager(), callback, null);

        assertTrue(button.attachFile(file("a.txt", 1)));
        assertTrue(button.attachFile(file("b.txt", 1)));
        assertFalse(button.attachFile(file("c.txt", 1)));
        assertEquals("limit", callback.events.get(callback.events.size() - 1));
    }

    @Test
    void attachFilesReturnsAddedCountAndSkipsOversize() throws IOException {
        RecordingCallback callback = new RecordingCallback();
        AttachmentManager manager = new AttachmentManager();
        AttachmentButton button = new AttachmentButton(manager, callback, null);

        Path small = Files.writeString(tempDir.resolve("small.txt"), "hello");
        Path big = tempDir.resolve("big.bin");
        try (RandomAccessFile raf = new RandomAccessFile(big.toFile(), "rw")) {
            raf.setLength(AttachmentManager.MAX_ATTACHMENT_SIZE + 1);
        }
        Path small2 = Files.writeString(tempDir.resolve("small2.txt"), "world");

        int added = button.attachFiles(new File[] {
                small.toFile(), big.toFile(), small2.toFile()});
        assertEquals(2, added);
        assertEquals(2, manager.size());
        assertEquals(List.of("filesAttached:2"), callback.events);
    }
}
