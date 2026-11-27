package github.anandb.netbeans.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Writes assistant-bubble content to the configured save directory. Pure filesystem
 * helper: the caller runs it off the EDT and reports the returned path.
 */
public final class AssistantMessageSaver {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private AssistantMessageSaver() {
    }

    /**
     * Writes {@code content} as a markdown file under {@code directory}, creating the
     * directory when missing.
     *
     * @param content  the message body; UTF-8
     * @param directory target folder
     * @param baseName hint for the file name (session or message id); sanitized
     * @return the file actually written
     * @throws IOException when the directory cannot be created or the file cannot be written
     */
    public static Path save(String content, Path directory, String baseName) throws IOException {
        if (directory == null) {
            throw new IOException("No save directory configured");
        }
        Files.createDirectories(directory);
        String stamp = LocalDateTime.now().format(STAMP);
        String base = sanitize(baseName);
        Path file = directory.resolve("beanbot-" + base + "-" + stamp + ".md");
        for (int n = 2; Files.exists(file); n++) {
            file = directory.resolve("beanbot-" + base + "-" + stamp + "-" + n + ".md");
        }
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        return file;
    }

    /** Keeps [A-Za-z0-9._-] and collapses everything else, so the name is portable. */
    static String sanitize(String baseName) {
        if (baseName == null || baseName.isBlank()) {
            return "assistant";
        }
        String cleaned = baseName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? "assistant" : cleaned;
    }
}
