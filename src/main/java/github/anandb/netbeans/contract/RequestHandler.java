package github.anandb.netbeans.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface RequestHandler {
    CompletableFuture<JsonNode> handle(JsonNode params);
}
