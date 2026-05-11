package github.anandb.netbeans.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;

/**
 * Handles incoming JSON-RPC requests from the ACP server.
 * Registered via {@code AcpProtocolClient.onRequest}.
 */
@FunctionalInterface
public interface RequestHandler {

    /**
     * Processes an incoming server request.
     *
     * @param params the request parameters as a JSON node
     * @return future resolving to the JSON result to send back
     */
    CompletableFuture<JsonNode> handle(JsonNode params);
}
