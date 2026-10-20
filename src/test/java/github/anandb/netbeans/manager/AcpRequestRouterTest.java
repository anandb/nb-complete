package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import github.anandb.netbeans.support.MapperSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests the ACP {@code fs/read_text_file} line/limit window. */
class AcpRequestRouterTest {

    private static final String CONTENT = "one\ntwo\nthree\nfour\n";

    @Test
    void handleReadTextFileAppliesLineWindow(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sample.txt");
        Files.writeString(file, CONTENT);
        ObjectNode params = MapperSupplier.get().createObjectNode();
        params.put("path", file.toString());
        params.put("line", 2);
        params.put("limit", 2);

        JsonNode result = new AcpRequestRouter().handleReadTextFile(params).get(5, TimeUnit.SECONDS);

        assertEquals("two\nthree\n", result.get("content").asText());
    }

    @Test
    void absentLineAndLimitReturnsWholeContent() {
        assertEquals(CONTENT, AcpRequestRouter.selectLines(CONTENT, 1, null));
    }

    @Test
    void startLineWithoutLimitReadsToEnd() {
        assertEquals("two\nthree\nfour\n", AcpRequestRouter.selectLines(CONTENT, 2, null));
    }

    @Test
    void limitCountsLinesFromStartLine() {
        assertEquals("one\ntwo\n", AcpRequestRouter.selectLines(CONTENT, 1, 2));
        assertEquals("two\n", AcpRequestRouter.selectLines(CONTENT, 2, 1));
        assertEquals("three\nfour\n", AcpRequestRouter.selectLines(CONTENT, 3, 10));
    }

    @Test
    void startLinePastEndReturnsEmpty() {
        assertEquals("", AcpRequestRouter.selectLines(CONTENT, 5, null));
        assertEquals("", AcpRequestRouter.selectLines(CONTENT, 5, 3));
    }

    @Test
    void nonPositiveLimitReturnsEmpty() {
        assertEquals("", AcpRequestRouter.selectLines(CONTENT, 1, 0));
        assertEquals("", AcpRequestRouter.selectLines(CONTENT, 1, -4));
    }

    @Test
    void lastLineWithoutTrailingNewlineIsIncluded() {
        assertEquals("four", AcpRequestRouter.selectLines("one\ntwo\nthree\nfour", 4, null));
        assertEquals("three\nfour", AcpRequestRouter.selectLines("one\ntwo\nthree\nfour", 3, 5));
    }

    @Test
    void emptyContentReturnsEmptyForAnyWindow() {
        assertEquals("", AcpRequestRouter.selectLines("", 1, null));
        assertEquals("", AcpRequestRouter.selectLines("", 2, 1));
    }
}
