package github.anandb.netbeans.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the layer against dangling references — both the hand-written {@code layer.xml} and the
 * one the NetBeans annotation processor generates from {@code @ActionRegistration},
 * {@code @TopComponent} and {@code @OptionsPanelController}. Class names and bundle keys there are
 * plain strings, so moving or renaming a class compiles cleanly and only fails in the running IDE
 * (a menu item that does nothing, a shortcut that stops working, a label showing a raw key). This
 * test fails fast instead.
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
        List<String> missing = missingBundleKeys(source());
        assertTrue(missing.isEmpty(), "layer.xml names bundle keys that no longer exist: " + missing);
    }

    /**
     * Covers the entries the annotation processor emits: a moved {@code @ActionRegistration} class
     * resolves {@code Bundle.properties} from its own package, so its keys must live there, and the
     * build only enforces that the file exists — not that the key is in it.
     */
    @Test
    void everyBundleKeyReferencedByAGeneratedEntryExists() throws IOException {
        URL generated = LayerReferencesTest.class.getResource("/META-INF/generated-layer.xml");
        Assumptions.assumeTrue(generated != null,
                "generated-layer.xml not found on the classpath — run through Maven to check it");
        String xml;
        try (InputStream in = generated.openStream()) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> missing = missingBundleKeys(xml);
        assertTrue(missing.isEmpty(),
                "annotation-generated layer entries name missing bundle keys: " + missing);
    }

    private static List<String> missingBundleKeys(String xml) throws IOException {
        List<String> missing = new ArrayList<>();
        Matcher m = BUNDLE_ATTR.matcher(xml);
        while (m.find()) {
            String pkg = m.group(1);
            if (!pkg.startsWith("github.anandb.")) {
                continue;
            }
            Path bundle = RES_ROOT.resolve(pkg.replace('.', '/') + ".properties");
            if (!Files.isRegularFile(bundle) || !propertyKeys(bundle).contains(m.group(2))) {
                missing.add(pkg + "#" + m.group(2));
            }
        }
        return missing;
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
