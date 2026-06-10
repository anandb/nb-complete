package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.contract.ToolExecutor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Adapts McpManager to the ToolExecutor port.
 */
public class McpToolAdapter implements ToolExecutor {

    private final McpManager delegate;

    public McpToolAdapter(McpManager delegate) {
        this.delegate = delegate;
    }

    @Override public void start() { delegate.start(); }
    @Override public void stop() { delegate.stop(); }
    @Override public CompletableFuture<Void> waitForReady() { return delegate.waitForReady(); }
    @Override public void disable() { delegate.disable(); }
    @Override public boolean isDisabled() { return delegate.isDisabled(); }
    @Override public void checkServerSupport(JsonNode res) { delegate.checkServerSupport(res); }
    @Override public List<Map<String, Object>> getServerConfig() { return delegate.getServerConfig(); }
}
