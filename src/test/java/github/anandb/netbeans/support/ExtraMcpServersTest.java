package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExtraMcpServersTest {

    @Test
    void httpExampleIsStrictAcpShape() {
        List<Map<String, Object>> servers = ExtraMcpServers.parse(ExtraMcpServers.HTTP_EXAMPLE);

        assertEquals(1, servers.size());
        Map<String, Object> server = servers.get(0);
        assertEquals("http", server.get("type"));
        assertEquals("example-http", server.get("name"));
        assertEquals("https://mcp.example.com/mcp", server.get("url"));
        assertEquals(2, ((List<?>) server.get("headers")).size());
    }

    @Test
    void stdioExampleOmitsTheTypeDiscriminatorAndCarriesEnv() {
        List<Map<String, Object>> servers = ExtraMcpServers.parse(ExtraMcpServers.STDIO_EXAMPLE);

        assertEquals(1, servers.size());
        Map<String, Object> server = servers.get(0);
        assertFalse(server.containsKey("type"));
        assertEquals("/absolute/path/to/mcp-server", server.get("command"));
        assertEquals(List.of("--option", "value"), server.get("args"));
        assertEquals(2, ((List<?>) server.get("env")).size());
    }

    @Test
    void rejectsNonArrayAndUnknownType() {
        assertThrows(IllegalArgumentException.class, () -> ExtraMcpServers.parse("{}"));
        assertThrows(IllegalArgumentException.class,
                () -> ExtraMcpServers.parse("[{\"type\":\"websocket\",\"name\":\"x\"}]"));
    }

    @Test
    void rejectsMissingRequiredFields() {
        // HTTP and SSE require headers.
        assertThrows(IllegalArgumentException.class,
                () -> ExtraMcpServers.parse("[{\"type\":\"http\",\"name\":\"x\",\"url\":\"https://x\"}]"));
        // stdio requires args and env.
        assertThrows(IllegalArgumentException.class,
                () -> ExtraMcpServers.parse("[{\"name\":\"x\",\"command\":\"/bin/x\",\"args\":[]}]"));
        assertThrows(IllegalArgumentException.class,
                () -> ExtraMcpServers.parse("[{\"name\":\"x\",\"command\":\"/bin/x\"}]"));
    }

    @Test
    void rejectsReservedAndDuplicateNames() {
        assertThrows(IllegalArgumentException.class, () -> ExtraMcpServers.parse(
                "[{\"name\":\"nb\",\"command\":\"/bin/x\",\"args\":[],\"env\":[]}]"));
        assertThrows(IllegalArgumentException.class, () -> ExtraMcpServers.parse(
                "[{\"name\":\"same\",\"command\":\"/bin/x\",\"args\":[],\"env\":[]},"
                        + "{\"name\":\"same\",\"command\":\"/bin/y\",\"args\":[],\"env\":[]}]"));
    }

    @Test
    void acceptsEmptyPairValues() {
        // A set-but-empty env value is legitimate; only the name must be non-empty.
        List<Map<String, Object>> servers = ExtraMcpServers.parse(
                "[{\"name\":\"x\",\"command\":\"/bin/x\",\"args\":[],"
                        + "\"env\":[{\"name\":\"EMPTY\",\"value\":\"\"}]}]");

        assertEquals(1, servers.size());
        assertThrows(IllegalArgumentException.class, () -> ExtraMcpServers.parse(
                "[{\"name\":\"x\",\"command\":\"/bin/x\",\"args\":[],"
                        + "\"env\":[{\"name\":\"\",\"value\":\"v\"}]}]"));
        assertThrows(IllegalArgumentException.class, () -> ExtraMcpServers.parse(
                "[{\"name\":\"x\",\"command\":\"/bin/x\",\"args\":[],"
                        + "\"env\":[{\"name\":\"N\",\"value\":1}]}]"));
    }

    @Test
    void acceptsExplicitStdioType() {
        List<Map<String, Object>> servers = ExtraMcpServers.parse(
                "[{\"type\":\"stdio\",\"name\":\"x\",\"command\":\"/bin/x\",\"args\":[],\"env\":[]}]");

        assertEquals("stdio", servers.get(0).get("type"));
    }

    @Test
    void insertExampleKeepsExistingServers() {
        String combined = ExtraMcpServers.insertExample(ExtraMcpServers.HTTP_EXAMPLE, ExtraMcpServers.STDIO_EXAMPLE);

        List<Map<String, Object>> servers = ExtraMcpServers.parse(combined);
        assertEquals(2, servers.size());
        assertEquals("example-http", servers.get(0).get("name"));
        assertEquals("example-stdio", servers.get(1).get("name"));
    }

    @Test
    void insertExampleReplacesBlankOrInvalidText() {
        assertEquals(ExtraMcpServers.STDIO_EXAMPLE,
                ExtraMcpServers.insertExample("", ExtraMcpServers.STDIO_EXAMPLE));
        assertEquals(ExtraMcpServers.STDIO_EXAMPLE,
                ExtraMcpServers.insertExample("not json", ExtraMcpServers.STDIO_EXAMPLE));
        // A duplicate name no longer wipes the draft: the example replaces its
        // own entry, so the result is the refreshed example content.
        List<Map<String, Object>> refreshed = ExtraMcpServers.parse(
                ExtraMcpServers.insertExample(ExtraMcpServers.HTTP_EXAMPLE, ExtraMcpServers.HTTP_EXAMPLE));
        assertEquals(1, refreshed.size());
        assertEquals("example-http", refreshed.get(0).get("name"));
        assertEquals("https://mcp.example.com/mcp", refreshed.get(0).get("url"));
    }

    @Test
    void insertExampleUpdatesTheDuplicateAndKeepsUnrelatedServers() {
        String current = "[{\"name\":\"example-http\",\"type\":\"http\","
                + "\"url\":\"https://old.example/mcp\",\"headers\":[]},"
                + "{\"name\":\"mine\",\"type\":\"http\",\"url\":\"https://my.example/mcp\",\"headers\":[]}]";

        List<Map<String, Object>> servers =
                ExtraMcpServers.parse(ExtraMcpServers.insertExample(current, ExtraMcpServers.HTTP_EXAMPLE));

        assertEquals(2, servers.size());
        Map<String, Object> updated = servers.stream()
                .filter(s -> "example-http".equals(s.get("name"))).findFirst().orElseThrow();
        assertEquals("https://mcp.example.com/mcp", updated.get("url"),
                "the example replaces the stale entry, not the whole draft");
        assertTrue(servers.stream().anyMatch(s -> "mine".equals(s.get("name"))),
                "unrelated servers survive a repeated example click");
    }

    @Test
    void prettyOutputRoundTrips() {
        List<Map<String, Object>> servers = ExtraMcpServers.parse(ExtraMcpServers.HTTP_EXAMPLE);
        String pretty = ExtraMcpServers.pretty(servers);

        assertTrue(pretty.contains("\"type\" : \"http\""));
        assertEquals(servers, ExtraMcpServers.parse(pretty));
    }

    @Test
    void parseStoredIgnoresInvalidContent() {
        assertTrue(ExtraMcpServers.parseStored("not json").isEmpty());
        assertTrue(ExtraMcpServers.parseStored("  ").isEmpty());
        assertEquals(1, ExtraMcpServers.parseStored(ExtraMcpServers.HTTP_EXAMPLE).size());
    }
}
