package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the {@code .properties} resources against mojibake. An editor that reads UTF-8
 * as ISO-8859-1 and saves with {@code native2ascii}-style escaping turns an ellipsis into
 * {@code \u00e2\u0080\u00a6}, which the IDE then renders as "â€¦".
 */
class PropertiesEncodingTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /** Latin-1 reads of UTF-8 bytes, in both their escaped and their literal spelling. */
    private static final List<String> MOJIBAKE = List.of(
            "\\u00e2\\u0080", "\\u00e2\\u0082", "\\u00e2\\u0084",
            "\\u00c3", "\\u00c2",
            "â€", "Ã", "Â");

    @Test
    void detectorCatchesTheEscapedEllipsis() {
        assertTrue(flag("LBL_DeleteSession=Delete Session\\u00e2\\u0080\\u00a6").contains("\\u00e2\\u0080"),
                "the guard must recognise the escaping that broke the menu labels");
        assertTrue(flag("LBL_DeleteSession=Delete Sessionâ€¦").contains("â€"),
                "the guard must recognise the literal mojibake spelling");
        assertTrue(flag("LBL_DeleteSession=Delete Session…").isEmpty(),
                "a correctly encoded file must pass");
    }

    @Test
    void resourcesAreUtf8WithoutMojibake() throws IOException {
        assumeTrue(Files.isDirectory(RESOURCES), "run from the project root");

        List<Path> propertiesFiles;
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            propertiesFiles = files.filter(p -> p.getFileName().toString().endsWith(".properties")).toList();
        }
        assumeTrue(!propertiesFiles.isEmpty(), "no .properties resources found");

        List<String> problems = new ArrayList<>();
        for (Path file : propertiesFiles) {
            String text = decodeStrictly(file, problems);
            if (text == null) {
                continue;
            }
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                List<String> hits = flag(lines[i]);
                if (!hits.isEmpty()) {
                    problems.add(file + ":" + (i + 1) + " contains " + hits
                            + " — save the file as UTF-8: " + lines[i].trim());
                }
            }
        }
        assertTrue(problems.isEmpty(), () -> "mojibake in properties resources:\n"
                + String.join("\n", problems));
    }

    /** The mojibake markers a line carries, empty when it reads as plain text. */
    private static List<String> flag(String line) {
        List<String> hits = new ArrayList<>();
        for (String marker : MOJIBAKE) {
            if (line.contains(marker)) {
                hits.add(marker);
            }
        }
        return hits;
    }

    /** Decodes as strict UTF-8 so an invalid byte is reported instead of silently replaced. */
    private static String decodeStrictly(Path file, List<String> problems) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(file)))
                    .toString();
        } catch (CharacterCodingException e) {
            problems.add(file + " is not valid UTF-8: " + e);
            return null;
        } catch (IOException e) {
            problems.add(file + " could not be read: " + e);
            return null;
        }
    }
}
