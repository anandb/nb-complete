package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import java.util.logging.Level;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Decodes a raw {@code session/update} notification params node into a {@link SessionUpdate}.
 *
 * <p>Extracted verbatim from the inline listener in {@code ServerProcessLifecycle.startServer()}
 * so the wire-shape handling is reachable from headless tests. Two wire shapes exist:
 * normal updates carry an {@code update} wrapper object, while textual turn-end signals
 * ({@code responding_finished}/{@code end_turn}) arrive as a bare {@code sessionUpdate}
 * string that Jackson would drop when mapping {@code Params} directly.</p>
 */
final class SessionUpdateDecoder {

    private static final Logger LOG = Logger.from(SessionUpdateDecoder.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private SessionUpdateDecoder() {
    }

    /**
     * @return the decoded update, or {@code null} when the payload is undecodable (dropped, logged).
     */
    static SessionUpdate decode(JsonNode params) {
        if (params == null || params.isNull()) {
            LOG.fine("Dropping session/update with null params");
            return null;
        }
        // Extract raw type before parse (needed in catch block too)
        String rawType = null;
        try {
            // Detect responding_finished/end_turn before Jackson drops them
            JsonNode updateNode = params != null ? params.get("sessionUpdate") : null;
            if (updateNode != null) {
                if (updateNode.isTextual()) {
                    rawType = updateNode.asText();
                } else if (updateNode.isObject() && updateNode.has("type")) {
                    JsonNode typeNode = updateNode.get("type");
                    rawType = typeNode != null && typeNode.isTextual() ? typeNode.asText() : null;
                }
            }

            // Construct synthetic SessionUpdate for textual turn-end signals
            // before Jackson treeToValue drops them (they lack the "update" wrapper object)
            if ("responding_finished".equals(rawType) || "end_turn".equals(rawType)) {
                LOG.fine("SSE turn-end signal received via textual sessionUpdate: {0}", rawType);
                MessageType mt = MessageType.valueOf(rawType);
                String ssId = params != null && params.has("sessionId") ? params.get("sessionId").asText() : null;
                return SessionUpdate.syntheticTurnEnd(ssId, mt);
            }

            SessionUpdate.Params sessionParams = MAPPER.treeToValue(params, SessionUpdate.Params.class);
            return new SessionUpdate(SessionUpdate.JSONRPC_VERSION, SessionUpdate.METHOD, sessionParams);
        } catch (Exception e) {
            LOG.log(rawType != null ? Level.INFO : Level.FINE,
                "Failed to parse session/update notification: {0}", ExceptionUtils.getMessage(e), e);
            return null;
        }
    }
}
