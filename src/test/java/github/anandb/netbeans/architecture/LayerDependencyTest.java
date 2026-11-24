package github.anandb.netbeans.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the hexagonal layer rules from docs/agent/architecture.md over the whole main tree:
 * dependencies may point down to {@code contract/}/{@code model/}, never up or sideways.
 *
 * <p>Accepted debt must be listed in {@link #EXCEPTIONS} with the exact files that carry
 * it, so a new coupling fails here instead of quietly widening the debt registry.
 */
class LayerDependencyTest {

    private static final Path MAIN = Path.of("src/main/java/github/anandb/netbeans");
    private static final String PREFIX = "github.anandb.netbeans.";
    private static final Pattern IMPORT = Pattern.compile(
            "^import\\s+(?:static\\s+)?" + Pattern.quote(PREFIX) + "([\\w.]+);", Pattern.MULTILINE);

    /** Top-level packages whose edges are policed; a new one must be classified here. */
    private static final Set<String> LAYERS = Set.of("contract", "db", "manager", "mcp", "model",
            "pattern", "project", "stereotype", "support", "tasks", "ui");

    /** Edges that no file may have. Accepted debt is re-admitted per file in EXCEPTIONS. */
    private static final Set<String> FORBIDDEN = Set.of(
            "manager -> ui",
            "ui -> manager", "ui -> mcp",
            "model -> ui", "model -> manager", "model -> mcp", "model -> db",
            "model -> contract", "model -> support", "model -> tasks", "model -> project",
            "contract -> ui", "contract -> manager", "contract -> mcp", "contract -> db",
            "contract -> support", "contract -> tasks", "contract -> project",
            "support -> ui", "support -> manager", "support -> mcp", "support -> db",
            "support -> tasks", "support -> project", "support -> model",
            "db -> ui", "db -> manager", "db -> mcp",
            "tasks -> manager", "tasks -> mcp",
            "project -> ui", "project -> manager", "project -> mcp");

    /** Accepted debt: edge -> the only files allowed to carry it (paths relative to MAIN). */
    private static final Map<String, Set<String>> EXCEPTIONS = Map.of(
            "support -> model", Set.of(
                    "support/BinaryResolver.java",
                    "support/HarnessSelection.java",
                    "support/PluginSettings.java",
                    "support/PromptUsageRows.java",
                    "support/TaskTxtCodec.java",
                    "support/ToolDataExtractor.java",
                    "support/TurnTokenCounter.java"),
            "manager -> mcp", Set.of("manager/ProcessManager.java"),
            "tasks -> ui", Set.of(
                    "tasks/TaskIssueController.java",
                    "tasks/TaskRepositoryController.java"));

    @Test
    void mainTreeIsScanned() throws IOException {
        assertTrue(Files.isDirectory(MAIN), "missing main source tree: " + MAIN.toAbsolutePath());
        assertTrue(javaFiles().size() > 300, "layer scan saw suspiciously few files");
    }

    @Test
    void noForbiddenLayerEdges() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String rel = relative(file);
            String src = layerOf(rel);
            if (!LAYERS.contains(src)) {
                violations.add(rel + ": unclassified layer '" + src + "'");
                continue;
            }
            for (String dep : dependencies(file)) {
                if (dep.equals(src) || !LAYERS.contains(dep)) {
                    continue;
                }
                String edge = src + " -> " + dep;
                if (!FORBIDDEN.contains(edge)) {
                    continue;
                }
                Set<String> allowed = EXCEPTIONS.get(edge);
                if (allowed == null || !allowed.contains(rel)) {
                    violations.add(rel + ": " + edge);
                }
            }
        }
        assertTrue(violations.isEmpty(), "forbidden layer edges:\n" + String.join("\n", violations));
    }

    @Test
    void exceptionsStillExistAndStillApply() throws IOException {
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : EXCEPTIONS.entrySet()) {
            String dep = entry.getKey().split(" -> ")[1];
            for (String rel : entry.getValue()) {
                Path file = MAIN.resolve(rel);
                if (!Files.isRegularFile(file)) {
                    stale.add(rel + ": file is gone");
                } else if (!dependencies(file).contains(dep)) {
                    stale.add(rel + ": no longer imports " + dep);
                }
            }
        }
        assertTrue(stale.isEmpty(), "stale debt registry entries (update docs/agent/architecture.md):\n"
                + String.join("\n", stale));
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(MAIN)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String relative(Path file) {
        return MAIN.relativize(file).toString().replace('\\', '/');
    }

    private static String layerOf(String relativePath) {
        int slash = relativePath.indexOf('/');
        return slash < 0 ? "(root)" : relativePath.substring(0, slash);
    }

    /** Top-level layers this file imports; the import's last segment is the type name. */
    private static Set<String> dependencies(Path file) throws IOException {
        Set<String> deps = new LinkedHashSet<>();
        Matcher matcher = IMPORT.matcher(Files.readString(file));
        while (matcher.find()) {
            deps.add(matcher.group(1).split("\\.")[0]);
        }
        return deps;
    }
}
