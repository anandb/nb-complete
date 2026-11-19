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
        assertEquals(ExtraMcpServers.HTTP_EXAMPLE,
                ExtraMcpServers.insertExample(ExtraMcpServers.HTTP_EXAMPLE, ExtraMcpServers.HTTP_EXAMPLE));
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
