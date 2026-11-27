package github.anandb.netbeans.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantMessageSaverTest {

    @TempDir
    Path tempDir;

    @Test
    void writesContentAndCreatesMissingDirectory() throws IOException {
        Path dir = tempDir.resolve("nested/save-here");

        Path written = AssistantMessageSaver.save("# Answer\nbody", dir, "session-1");

        assertTrue(Files.isDirectory(dir));
        assertEquals("# Answer\nbody", Files.readString(written, StandardCharsets.UTF_8));
        assertTrue(written.getFileName().toString().startsWith("beanbot-session-1-"));
        assertEquals(".md", written.getFileName().toString()
                .substring(written.getFileName().toString().lastIndexOf('.')));
    }

    @Test
    void sanitizesBaseNameIntoTimestampedMarkdown() throws IOException {
        Path written = AssistantMessageSaver.save("x", tempDir, "Session/ID 42\"<>");

        String name = written.getFileName().toString();
        assertTrue(name.matches("beanbot-session-id-42-[0-9-]+\\.md"), name);
    }

    @Test
    void fallsBackToAssistantWhenBaseNameIsBlankOrNull() {
        assertEquals("assistant", AssistantMessageSaver.sanitize(null));
        assertEquals("assistant", AssistantMessageSaver.sanitize("   "));
        assertEquals("assistant", AssistantMessageSaver.sanitize("///"));
    }

    @Test
    void repeatedSavesProduceDistinctFiles() throws IOException {
        Path first = AssistantMessageSaver.save("a", tempDir, "same");
        Path second = AssistantMessageSaver.save("b", tempDir, "same");

        assertNotEquals(first, second);
        List<Path> files;
        try (var stream = Files.list(tempDir)) {
            files = stream.toList();
        }
        assertEquals(2, files.size());
    }
}
