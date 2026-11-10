package github.anandb.netbeans.model;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachedFileTest {

    @TempDir
    Path temp;

    @Test
    void detectsDeclaredImageAndEncodesOriginalBytes() {
        byte[] data = {0, 1, 2};
        AttachedFile file = new AttachedFile("image.png", "image/png", data, data.length, "file:///image.png");
        assertTrue(file.isImage());
        assertNull(file.textContent());
        assertEquals(Base64.getEncoder().encodeToString(data), file.base64Data());
    }

    @Test
    void decodesTextAndDropsUtf8Bom() {
        byte[] data = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, 'h', 'i'};
        AttachedFile file = new AttachedFile("a.txt", "text/plain", data, data.length, "file:///a.txt");
        assertFalse(file.isImage());
        assertEquals("hi", file.textContent());
    }

    @Test
    void sniffsUnknownTextAndRejectsBinaryAndInvalidUtf8() {
        assertEquals("notes", unknown("notes").textContent());
        assertNull(unknown("a\0b").textContent());
        assertNull(new AttachedFile("raw.bin", "application/octet-stream", new byte[] {(byte) 0xff}, 1,
                "file:///raw.bin").textContent());
        assertNull(new AttachedFile("raw.bin", "application/pdf", "text".getBytes(StandardCharsets.UTF_8), 4,
                "file:///raw.bin").textContent());
    }

    @Test
    void fileConstructorReadsBytesAndRecognizesMimeTypes() throws Exception {
        Path image = temp.resolve("PHOTO.JPEG");
        Files.write(image, new byte[] {1, 2});
        AttachedFile attachedImage = new AttachedFile(image.toFile());
        assertEquals("image/jpeg", attachedImage.mimeType());
        assertEquals(2, attachedImage.size());
        assertTrue(attachedImage.uri().startsWith("file:"));

        Path text = temp.resolve("notes.log");
        Files.writeString(text, "hello");
        assertEquals("hello", new AttachedFile(text.toFile()).textContent());
        Path unknown = temp.resolve("Makefile");
        Files.writeString(unknown, "all: build");
        assertEquals("all: build", new AttachedFile(unknown.toFile()).textContent());
    }

    @Test
    void recognizesSupportedFileExtensions() throws Exception {
        String[][] extensions = {
            {"png", "image/png"}, {"jpg", "image/jpeg"}, {"gif", "image/gif"}, {"svg", "image/svg+xml"},
            {"webp", "image/webp"}, {"csv", "text/csv"}, {"odt", "application/vnd.oasis.opendocument.text"},
            {"doc", "application/msword"}, {"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
            {"xls", "application/vnd.ms-excel"}, {"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
            {"ppt", "application/vnd.ms-powerpoint"},
            {"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
            {"pdf", "application/pdf"}, {"json", "application/json"}, {"py", "text/x-python"},
            {"java", "text/x-java"}, {"md", "text/markdown"}, {"xml", "text/html"}, {"yaml", "text/yaml"},
            {"toml", "text/toml"}, {"sh", "text/x-sh"}, {"js", "text/javascript"},
            {"ts", "text/x-typescript"}, {"css", "text/css"}, {"scss", "text/x-scss"}, {"sql", "text/x-sql"},
            {"c", "text/x-c"}, {"cpp", "text/x-c++"}, {"rs", "text/x-rust"}, {"go", "text/x-go"},
            {"kt", "text/x-kotlin"}, {"rb", "text/x-ruby"}, {"php", "text/x-php"}, {"cs", "text/x-csharp"},
            {"swift", "text/x-swift"}, {"gradle", "text/x-groovy"}, {"diff", "text/x-diff"},
            {"unknown", "application/octet-stream"}
        };
        for (String[] entry : extensions) {
            Path path = temp.resolve("sample." + entry[0]);
            Files.write(path, new byte[0]);
            assertEquals(entry[1], new AttachedFile(path.toFile()).mimeType(), entry[0]);
        }
    }

    @Test
    void trimsOnlyAUtf8CharacterSplitAtSniffLimit() {
        byte[] data = new byte[8 * 1024 + 1];
        java.util.Arrays.fill(data, 0, 8 * 1024 - 1, (byte) 'a');
        data[8 * 1024 - 1] = (byte) 0xe2;
        data[8 * 1024] = (byte) 0x82;
        AttachedFile split = new AttachedFile("large.unknown", "application/octet-stream", data, data.length,
                "file:///large.unknown");
        assertTrue(split.textContent() != null);
    }

    private static AttachedFile unknown(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        return new AttachedFile("unknown.bin", "application/octet-stream", data, data.length, "file:///unknown.bin");
    }
}
