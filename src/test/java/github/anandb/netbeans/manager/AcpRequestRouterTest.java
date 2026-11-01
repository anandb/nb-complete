package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import github.anandb.netbeans.support.AcpFsPolicy;
import github.anandb.netbeans.support.MapperSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        AcpRequestRouter router = routerFor(dir, AcpFsPolicy.ALLOW);
        JsonNode result = router.handleReadTextFile(params).get(5, TimeUnit.SECONDS);

        assertEquals("two\nthree\n", result.get("content").asText());
    }

    private static AcpRequestRouter routerFor(Path projectDirectory, AcpFsPolicy policy) {
        AcpRequestRouter router = new AcpRequestRouter();
        router.setSessionDirectoryResolver(sessionId -> projectDirectory.toString());
        router.setFsPolicySupplier(() -> policy);
        return router;
    }

    private static ObjectNode readParams(Path file) {
        ObjectNode params = MapperSupplier.get().createObjectNode();
        params.put("path", file.toString());
        params.put("sessionId", "s1");
        return params;
    }

    private static ObjectNode writeParams(Path file) {
        ObjectNode params = readParams(file);
        params.put("content", "x");
        return params;
    }

    private static String rejectionMessage(ExecutionException ex) {
        Throwable cause = ex.getCause();
        return cause != null ? cause.getMessage() : ex.getMessage();
    }

    @Test
    void readOutsideTheProjectDirectoryIsRejected(@TempDir Path dir) throws Exception {
        Path project = dir.resolve("project");
        Files.createDirectories(project);
        Path outside = dir.resolve("outside.txt");
        Files.writeString(outside, "secret");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> routerFor(project, AcpFsPolicy.ALLOW)
                        .handleReadTextFile(readParams(outside)).get(5, TimeUnit.SECONDS));

        assertTrue(rejectionMessage(ex).contains("outside the project directory"),
                "rejection must name the confinement, got: " + rejectionMessage(ex));
    }

    @Test
    void readAnyWriteProjectAllowsExternalReadsAndConfinesWrites(@TempDir Path dir) throws Exception {
        Path project = dir.resolve("project");
        Files.createDirectories(project);
        Path outside = dir.resolve("outside.txt");
        Files.writeString(outside, "external");
        AcpRequestRouter router = routerFor(project, AcpFsPolicy.READ_ANY_WRITE_PROJECT);

        JsonNode result = router.handleReadTextFile(readParams(outside)).get(5, TimeUnit.SECONDS);
        assertEquals("external", result.get("content").asText(),
                "this policy allows reads outside the project");

        Path outsideWrite = dir.resolve("outside-write.txt");
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> router.handleWriteTextFile(writeParams(outsideWrite)).get(5, TimeUnit.SECONDS));
        assertTrue(rejectionMessage(ex).contains("outside the project directory"),
                "writes stay confined to the project, got: " + rejectionMessage(ex));
        assertFalse(Files.exists(outsideWrite), "the rejected write must not create the file");
    }

    @Test
    void readOnlyPolicyRejectsWritesInsideTheProject(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("readonly.txt");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> routerFor(dir, AcpFsPolicy.READ_ONLY)
                        .handleWriteTextFile(writeParams(file)).get(5, TimeUnit.SECONDS));

        assertTrue(rejectionMessage(ex).contains("read-only"), rejectionMessage(ex));
        assertFalse(Files.exists(file), "a rejected write must not create the file");
    }

    @Test
    void secretFilesAreRejectedForReadAndWrite(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "TOKEN=secret");
        AcpRequestRouter router = routerFor(dir, AcpFsPolicy.READ_ANY_WRITE_PROJECT);

        ExecutionException read = assertThrows(ExecutionException.class,
                () -> router.handleReadTextFile(readParams(env)).get(5, TimeUnit.SECONDS));
        assertTrue(rejectionMessage(read).contains("secrets file"), rejectionMessage(read));

        Path key = dir.resolve("server.key");
        ExecutionException write = assertThrows(ExecutionException.class,
                () -> router.handleWriteTextFile(writeParams(key)).get(5, TimeUnit.SECONDS));
        assertTrue(rejectionMessage(write).contains("secrets file"), rejectionMessage(write));
        assertFalse(Files.exists(key));
    }

    @Test
    void protectedVcsDirectoriesRejectWrites(@TempDir Path dir) throws Exception {
        Path hook = dir.resolve(".git/hooks/pre-commit");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> routerFor(dir, AcpFsPolicy.ALLOW)
                        .handleWriteTextFile(writeParams(hook)).get(5, TimeUnit.SECONDS));

        assertTrue(rejectionMessage(ex).contains("protected"), rejectionMessage(ex));
        assertFalse(Files.exists(hook), "the refused write must not create the hook");
    }

    @Test
    void unknownProjectDirectoryRejectsAccess(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("inside.txt");
        Files.writeString(file, "x");
        AcpRequestRouter router = new AcpRequestRouter();
        router.setSessionDirectoryResolver(sessionId -> null);
        router.setFsPolicySupplier(() -> AcpFsPolicy.ALLOW);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> router.handleReadTextFile(readParams(file)).get(5, TimeUnit.SECONDS));

        assertTrue(rejectionMessage(ex).contains("No project directory"), rejectionMessage(ex));
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
