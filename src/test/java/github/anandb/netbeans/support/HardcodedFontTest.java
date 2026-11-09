package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Fonts must follow the user's settings. A hardcoded family name bypasses the Options font
 * setting and — because Swing does not fall back per glyph for a physical font — renders every
 * character the family lacks as an empty box. The only allowed literals are the JDK's logical
 * families, which are composite fonts with real fallback.
 */
class HardcodedFontTest {

    /** JDK logical families: composite fonts, so a missing glyph falls back. */
    private static final Set<String> LOGICAL =
            Set.of("Dialog", "DialogInput", "SansSerif", "Serif", "Monospaced");

    private static final Pattern LITERAL_FAMILY =
            Pattern.compile("new\\s+Font\\s*\\(\\s*\"([^\"]+)\"");

    @Test
    void noProductionCodeHardcodesAFontFamily() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = LITERAL_FAMILY.matcher(source);
            while (m.find()) {
                if (!LOGICAL.contains(m.group(1))) {
                    offenders.add(file.getFileName() + ": new Font(\"" + m.group(1) + "\"");
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "use ThemeManager/UIUtils fonts (or a Font.MONOSPACED-style logical family) so the "
                        + "user's font setting applies: " + offenders);
    }

    @Test
    void userVisibleStringsAvoidGlyphsThatNeedAnEmojiFont() throws Exception {
        // A physical font without the glyph, and Java 17 cannot render colour-emoji fonts, so these
        // come out as empty boxes on many setups. Keep UI strings to characters every text font has.
        String bundle = Files.readString(bundlePath(), StandardCharsets.UTF_8);

        for (String escaped : List.of("\\u2728", "\\uD83C", "\\uD83D", "\\u2727")) {
            assertTrue(!bundle.contains(escaped),
                    "UI strings must not use emoji (" + escaped + "): they render as empty boxes");
        }
    }

    private static Path bundlePath() throws URISyntaxException {
        return Path.of(HardcodedFontTest.class.getResource(
                "/github/anandb/netbeans/ui/Bundle.properties").toURI());
    }

    private static List<Path> sources() throws IOException {
        // Surefire runs with the project basedir as the working directory.
        Path main = Path.of("src/main/java");
        assertTrue(Files.isDirectory(main), "source root not found from " + main.toAbsolutePath());
        try (Stream<Path> files = Files.walk(main)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
