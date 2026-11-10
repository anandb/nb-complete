package github.anandb.netbeans.ui.rendering;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ThemeManager.getIcon("x.svg", n)} resolves against {@code ui/icons/}, so a renamed or
 * deleted file is a plain string that still compiles: the icon silently disappears and the log
 * fills with "Failed to load icon". This fails fast instead.
 */
class UiIconReferencesTest {

    private static final Pattern ICON_LITERAL = Pattern.compile("getIcon\\(\"([^\"]+\\.[a-z]+)\"");

    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path ICONS = PROJECT_ROOT.resolve("src/main/resources/github/anandb/netbeans/ui/icons");

    @Test
    void everyIconNamedByUiCodeExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path source : javaSources(PROJECT_ROOT.resolve("src/main/java"))) {
            Matcher m = ICON_LITERAL.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (m.find()) {
                String icon = m.group(1);
                if (!Files.isRegularFile(ICONS.resolve(icon))) {
                    missing.add(icon + " (" + PROJECT_ROOT.relativize(source) + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(), "ui/icons has no such file: " + missing);
    }

    private static List<Path> javaSources(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static Path findProjectRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/resources"))) {
            dir = dir.getParent();
        }
        return dir;
    }
}
