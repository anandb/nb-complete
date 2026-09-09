package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against MissingResourceException at runtime: every key declared in a
 * {@code @NbBundle.Messages} / {@code @NbBundle.Message} annotation MUST also
 * exist in the {@code Bundle.properties} of the annotated class's package
 * (under {@code src/main/resources}). The NetBeans annotation processor does
 * not merge annotation keys into the bundle at build time, so a cleanup that
 * deletes keys from the properties file only fails later, in the running IDE
 * (e.g. toolbar loading). This test fails fast instead.
 */
class BundleKeysConsistencyTest {

    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path JAVA_ROOT = PROJECT_ROOT.resolve("src/main/java");
    private static final Path RES_ROOT = PROJECT_ROOT.resolve("src/main/resources");

    /** Finds the project root (the directory containing src/main/resources). */
    private static Path findProjectRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/resources"))) {
            dir = dir.getParent();
        }
        return dir;
    }

    /** Keys inside a {@code @NbBundle.Messages({...})} block: "KEY=..." or "KEY". */
    private static final Pattern BLOCK_KEY =
            Pattern.compile("\"\\s*([A-Za-z0-9_.]+)\\s*(=|\"\\s*[,}])");

    /** Single-annotation form: key = "KEY". */
    private static final Pattern SINGLE_KEY =
            Pattern.compile("@NbBundle\\.Message\\s*\\([^)]*key\\s*=\\s*\"([A-Za-z0-9_.]+)\"",
                    Pattern.DOTALL);

    @Test
    void everyAnnotatedBundleKeyExistsInThePropertiesFile() throws IOException {
        // key -> packages that need it (report only the first offender per key)
        List<String> failures = new java.util.ArrayList<>();
        try (Stream<Path> javaFiles = Files.walk(JAVA_ROOT)) {
            for (Path javaFile : (Iterable<Path>) javaFiles
                    .filter(p -> p.toString().endsWith(".java"))::iterator) {
                String source = Files.readString(javaFile, StandardCharsets.UTF_8);
                if (!source.contains("@NbBundle.Message")) {
                    continue;
                }
                String pkg = packageName(source);
                Map<String, Integer> declared = declaredKeys(source);
                if (declared.isEmpty()) {
                    continue;
                }
                Path bundle = RES_ROOT.resolve(pkg.replace('.', '/'))
                        .resolve("Bundle.properties");
                if (!Files.isRegularFile(bundle)) {
                    failures.add(javaFile.getFileName() + ": no Bundle.properties at "
                            + RES_ROOT.relativize(bundle) + " for keys " + declared.keySet());
                    continue;
                }
                Set<String> existing = propertyKeys(bundle);
                for (String key : declared.keySet()) {
                    if (!existing.contains(key)) {
                        failures.add(String.format("%s declares key '%s' but it is missing "
                                + "from %s (NbBundle.getMessage will throw at runtime)",
                                javaFile.getFileName(), key,
                                RES_ROOT.relativize(bundle)));
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(),
                "Missing bundle keys (" + failures.size() + "):\n  "
                        + String.join("\n  ", failures));
    }

    /** Direct lookups: NbBundle.getMessage(SomeClass.class, "KEY", ...). */
    private static final Pattern DIRECT_LOOKUP =
            Pattern.compile("NbBundle\\.getMessage\\(\\s*([\\w.]+)\\.class\\s*,\\s*\"([A-Za-z0-9_.]+)\"");

    @Test
    void everyDirectGetMessageKeyExistsInThePropertiesFile() throws IOException {
        List<String> failures = new java.util.ArrayList<>();
        try (Stream<Path> javaFiles = Files.walk(JAVA_ROOT)) {
            for (Path javaFile : (Iterable<Path>) javaFiles
                    .filter(p -> p.toString().endsWith(".java"))::iterator) {
                String source = Files.readString(javaFile, StandardCharsets.UTF_8);
                if (!source.contains("NbBundle.getMessage")) {
                    continue;
                }
                Matcher m = DIRECT_LOOKUP.matcher(source);
                while (m.find()) {
                    String ownerClass = m.group(1);
                    String key = m.group(2);
                    // Keys ending in '_' are dynamic prefixes composed at runtime
                    // (e.g. "ACR_MessageBubble_" + role) — skip them.
                    if (key.endsWith("_")) {
                        continue;
                    }
                    String pkg = ownerClass.contains(".")
                            ? ownerClass.substring(0, ownerClass.lastIndexOf('.'))
                            : packageName(source);
                    Path bundle = RES_ROOT.resolve(pkg.replace('.', '/'))
                            .resolve("Bundle.properties");
                    if (!Files.isRegularFile(bundle)
                            || !propertyKeys(bundle).contains(key)) {
                        failures.add(String.format("%s looks up key '%s' via %s but it is "
                                        + "missing from %s (NbBundle.getMessage will throw)",
                                javaFile.getFileName(), key, ownerClass,
                                Files.isRegularFile(bundle) ? RES_ROOT.relativize(bundle) : bundle));
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(),
                "Missing direct-lookup bundle keys (" + failures.size() + "):\n  "
                        + String.join("\n  ", failures));
    }

    @Test
    void noDuplicateKeysInAnyBundleProperties() throws IOException {
        try (Stream<Path> props = Files.walk(RES_ROOT)) {
            for (Path bundle : (Iterable<Path>) props
                    .filter(p -> p.getFileName().toString().equals("Bundle.properties"))::iterator) {
                Map<String, Integer> seen = new HashMap<>();
                for (String line : Files.readAllLines(bundle, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    if (eq > 0) {
                        seen.merge(trimmed.substring(0, eq).trim(), 1, Integer::sum);
                    }
                }
                List<String> dups = seen.entrySet().stream()
                        .filter(e -> e.getValue() > 1)
                        .map(Map.Entry::getKey).toList();
                assertTrue(dups.isEmpty(),
                        bundle + " has duplicate keys: " + dups
                                + " (last one silently wins)");
            }
        }
    }

    /** Extracts the keys declared in @NbBundle.Messages blocks and single @NbBundle.Message. */
    private static Map<String, Integer> declaredKeys(String source) {
        Map<String, Integer> keys = new HashMap<>();
        int idx = 0;
        while ((idx = source.indexOf("@NbBundle.Messages({", idx)) >= 0) {
            int end = source.indexOf("})", idx);
            if (end < 0) {
                break;
            }
            String block = source.substring(idx, end);
            Matcher m = BLOCK_KEY.matcher(block);
            while (m.find()) {
                keys.merge(m.group(1), 1, Integer::sum);
            }
            idx = end;
        }
        Matcher single = SINGLE_KEY.matcher(source);
        while (single.find()) {
            keys.merge(single.group(1), 1, Integer::sum);
        }
        return keys;
    }

    private static String packageName(String source) {
        Matcher m = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE).matcher(source);
        return m.find() ? m.group(1) : "";
    }

    private static Set<String> propertyKeys(Path bundle) throws IOException {
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(bundle, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                keys.add(trimmed.substring(0, eq).trim());
            }
        }
        return keys;
    }
}
