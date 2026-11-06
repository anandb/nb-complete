package github.anandb.netbeans.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the hand-written {@code layer.xml} against dangling references. Class names and bundle
 * keys there are plain strings, so moving or renaming a class compiles cleanly and only fails in
 * the running IDE (a menu item that does nothing, a shortcut that stops working). This test fails
 * fast instead.
 */
class LayerReferencesTest {

    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path RES_ROOT = PROJECT_ROOT.resolve("src/main/resources");
    private static final Path LAYER = RES_ROOT.resolve("github/anandb/netbeans/ui/layer.xml");

    /** Attribute values that name a class, e.g. delegate/newvalue/instanceCreate. */
    private static final Pattern CLASS_ATTR =
            Pattern.compile("(?:newvalue|delegate|instanceCreate)=\"([\\w.$]+)\"");
    /** bundlevalue="some.pkg.Bundle#KEY". */
    private static final Pattern BUNDLE_ATTR =
            Pattern.compile("bundlevalue=\"([\\w.]+)#([\\w.]+)\"");

    @Test
    void everyClassNamedInTheLayerExists() throws IOException {
        List<String> missing = new ArrayList<>();
        Matcher m = CLASS_ATTR.matcher(source());
        while (m.find()) {
            String className = m.group(1);
            if (!className.startsWith("github.anandb.") || className.contains("#")) {
                continue;
            }
            try {
                Class.forName(className, false, LayerReferencesTest.class.getClassLoader());
            } catch (ClassNotFoundException ex) {
                missing.add(className);
            }
        }
        assertTrue(missing.isEmpty(), "layer.xml names classes that no longer exist: " + missing);
    }

    @Test
    void everyBundleKeyNamedInTheLayerExists() throws IOException {
        List<String> missing = new ArrayList<>();
        Matcher m = BUNDLE_ATTR.matcher(source());
        while (m.find()) {
            Path bundle = RES_ROOT.resolve(m.group(1).replace('.', '/') + ".properties");
            if (!Files.isRegularFile(bundle) || !propertyKeys(bundle).contains(m.group(2))) {
                missing.add(m.group(1) + "#" + m.group(2));
            }
        }
        assertTrue(missing.isEmpty(), "layer.xml names bundle keys that no longer exist: " + missing);
    }

    private static String source() throws IOException {
        return Files.readString(LAYER, StandardCharsets.UTF_8);
    }

    private static Set<String> propertyKeys(Path bundle) throws IOException {
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(bundle, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            int eq = trimmed.indexOf('=');
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && eq > 0) {
                keys.add(trimmed.substring(0, eq).trim());
            }
        }
        return keys;
    }

    private static Path findProjectRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("src/main/resources"))) {
            dir = dir.getParent();
        }
        return dir;
    }
}
