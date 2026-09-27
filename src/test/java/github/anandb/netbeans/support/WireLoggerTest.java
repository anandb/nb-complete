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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WireLoggerTest {

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
        assertEquals(
                List.of("{\"id\":0,\"method\":\"initialize\"}", "{\"id\":1,\"method\":\"session/list\"}"),
                Files.readAllLines(expected, StandardCharsets.UTF_8));
    }

    @Test
    void fallsBackToUnknownWhenNoHarnessIsGiven() throws IOException {
        try (WireLogger logger = new WireLogger(tmp.resolve("wire.log").toString(), null)) {
            logger.log("{\"id\":0,\"method\":\"initialize\"}");
        }

        Path expected = tmp.resolve("wire_" + today() + "-unknown.log");
        assertTrue(Files.exists(expected));
        assertEquals(List.of("{\"id\":0,\"method\":\"initialize\"}"),
                Files.readAllLines(expected, StandardCharsets.UTF_8));
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
}