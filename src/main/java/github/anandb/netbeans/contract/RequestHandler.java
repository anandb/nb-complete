package github.anandb.netbeans.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;

/** Handles incoming JSON-RPC requests from the ACP server. */
@FunctionalInterface
public interface RequestHandler {

    /** @return future resolving to the JSON result. */
    CompletableFuture<JsonNode> handle(JsonNode params);
}
