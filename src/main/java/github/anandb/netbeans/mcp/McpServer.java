package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class McpServer {

    private static final Logger LOG = new Logger(McpServer.class);
    private static final int MAX_THREADS = 20;

    private final ObjectMapper mapper = MapperSupplier.get();
    private final McpTools mcpTools = new McpTools(mapper);
    private Server server;
    private ServerConnector connector;
    private ExecutorService asyncExecutor;

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }

        asyncExecutor = Executors.newFixedThreadPool(
                MAX_THREADS,
                r -> {
                    Thread t = new Thread(r, "MCP-Async");
                    t.setDaemon(true);
                    return t;
                });

        EditorToolProvider editorTools = new EditorToolProvider();
        editorTools.registerTools(mcpTools);

        server = new Server(0);

        connector = new ServerConnector(server);
        connector.setIdleTimeout(30000);
        connector.setAcceptQueueSize(100);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");

        context.addServlet(new ServletHolder(new MessageServlet(mapper, asyncExecutor, mcpTools)), "/mcp");

        server.setHandler(context);
        try {
            server.start();
        } catch (Exception e) {
            throw new IOException("Failed to start Jetty server", e);
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
            asyncExecutor.shutdown();
            asyncExecutor = null;
        }
        LOG.info("MCP server stopped");
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    public String getUrl() {
        if (connector == null) {
            return null;
        }
        return "http://localhost:" + connector.getLocalPort() + "/mcp";
    }

    public McpTools getMcpTools() {
        return mcpTools;
    }
}
