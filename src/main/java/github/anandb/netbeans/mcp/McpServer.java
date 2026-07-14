package github.anandb.netbeans.mcp;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.logging.Level;

import github.anandb.netbeans.support.PluginSettings;
import org.openide.util.RequestProcessor;

import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

public class McpServer {

    private static final Logger LOG = Logger.from(McpServer.class);
    private static final int MAX_THREADS = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final McpTools mcpTools = new McpTools();
    private final String token;
    private Server server;
    private ServerConnector connector;
    private RequestProcessor asyncExecutor;

    public McpServer() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        token = sb.toString();
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }

        asyncExecutor = new RequestProcessor("MCP-Async", MAX_THREADS, true);

        EditorToolProvider editorTools = new EditorToolProvider();
        editorTools.registerTools(mcpTools);

        server = new Server(0);

        connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        // Clamp idle timeout: 0 or negative disables idle detection, which
        // leaks connections under load.  Minimum 30 s avoids undefined behaviour.
        int idleSec = PluginSettings.getSessionIdleTimeout();
        connector.setIdleTimeout(1000 * Math.max(30, idleSec));
        connector.setAcceptQueueSize(100);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");

        context.addServlet(new ServletHolder(new MessageServlet(asyncExecutor, mcpTools, token)), "/mcp");

        server.setHandler(context);
        try {
            server.start();
        } catch (Exception e) {
            throw new IOException(NbBundle.getMessage(McpServer.class, "ERR_StartJettyFailed"), e);
        }
        int actualPort = connector.getLocalPort();
        LOG.info("MCP server started on port {0}", actualPort);
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error stopping MCP server", e);
            }
            server = null;
            connector = null;
        }
        if (asyncExecutor != null) {
            asyncExecutor.stop();
            asyncExecutor = null;
        }
        LOG.info("MCP server stopped");
    }

    public synchronized boolean isRunning() {
        return server != null && server.isRunning();
    }

    public synchronized String getUrl() {
        if (connector == null) {
            return null;
        }
        return "http://127.0.0.1:" + connector.getLocalPort() + "/mcp?token=" + token;
    }

    public String getToken() {
        return token;
    }

    public McpTools getMcpTools() {
        return mcpTools;
    }
}
