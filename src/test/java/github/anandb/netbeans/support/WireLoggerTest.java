package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WireLoggerTest {

    private static final Pattern TIMESTAMP_PREFIX =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} ");
    @TempDir
    Path tmp;

    @Test
    void namesTheLogAfterTheHarnessItIsGiven() throws IOException {
        try (WireLogger logger = new WireLogger(tmp.resolve("wire.log").toString(), "pi-acp")) {
            logger.log("{\"id\":0,\"method\":\"initialize\"}");
            logger.log("{\"id\":1,\"method\":\"session/list\"}");
        }

        Path expected = tmp.resolve("wire_" + today() + "-pi-acp.log");
        assertTrue(Files.exists(expected), "log must be named after the harness");
        assertLogged(expected,
                "{\"id\":0,\"method\":\"initialize\"}", "{\"id\":1,\"method\":\"session/list\"}");
    }

    @Test
    void fallsBackToUnknownWhenNoHarnessIsGiven() throws IOException {
        try (WireLogger logger = new WireLogger(tmp.resolve("wire.log").toString(), null)) {
            logger.log("{\"id\":0,\"method\":\"initialize\"}");
        }

        Path expected = tmp.resolve("wire_" + today() + "-unknown.log");
        assertTrue(Files.exists(expected));
        assertLogged(expected, "{\"id\":0,\"method\":\"initialize\"}");
    }

    @Test
    void aBlankHarnessIsTreatedAsUnknown() throws IOException {
        try (WireLogger logger = new WireLogger(tmp.resolve("wire.log").toString(), "   ")) {
            logger.log("{\"id\":0}");
        }

        assertTrue(Files.exists(tmp.resolve("wire_" + today() + "-unknown.log")));
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /** Every logged line must be {@code <local time, no zone> <json>}. */
    private static void assertLogged(Path log, String... expectedJson) throws IOException {
        List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        assertEquals(expectedJson.length, lines.size());
        for (int i = 0; i < expectedJson.length; i++) {
            String line = lines.get(i);
            var matcher = TIMESTAMP_PREFIX.matcher(line);
            assertTrue(matcher.lookingAt(), "missing local timestamp prefix: " + line);
            assertEquals(expectedJson[i], line.substring(matcher.end()));
        }
    }
}