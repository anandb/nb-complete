package github.anandb.netbeans.ui;

import java.util.concurrent.CompletableFuture;

import javax.swing.SwingUtilities;

import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ToolContextExtractor;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.SessionService;

// DSL-CONTROLLER: not a view — NotifyDescriptor-based permission dialog
// orchestration (SessionControl lookup, prompt formatting). Stays imperative;
// the PermissionBubble leaf it produces is bound via PermissionBubbleSpec.
final class PermissionDialogManager {

    private static final Logger LOG = Logger.from(PermissionDialogManager.class);

    private final SessionService sessionService = Lookup.getDefault().lookup(PlatformBridge.class).sessionService();

    private final ChatThreadPanel chatPanel;

    PermissionDialogManager(ChatThreadPanel chatPanel) {
        this.chatPanel = chatPanel;
    }

    void handlePermissionRequest(String sessionId, JsonNode params,
            CompletableFuture<String> response, Runnable activateCallback) {
        SessionControl sessionControl = sessionService.get();
        String currentId = sessionControl != null ? sessionControl.getCurrentSessionId() : null;

        boolean isCurrent = currentId != null && currentId.equals(sessionId);
        boolean isDescendant = false;
        if (!isCurrent && sessionControl != null) {
            isDescendant = sessionControl.isDescendantOfCurrent(sessionId);
        }

        if (!isCurrent && !isDescendant) {
            LOG.info("Received permission request for unrelated session {0}, rejecting (current is {1})",
                    new Object[] { sessionId, currentId });
            response.complete("reject");
            return;
        }

        if (isDescendant) {
            LOG.fine("Received permission request for sub-agent session {0} of current session {1}",
                    new Object[] { sessionId, currentId });
        }

        String prompt = NbBundle.getMessage(PermissionDialogManager.class, "MSG_PermissionRequested");
        if (params.has("message")) {
            prompt = params.get("message").asText();
        } else if (params.has("content")) {
            prompt = params.get("content").asText();
        } else if (params.has("toolCall") || params.has("tool_call")) {
            JsonNode tc = params.has("toolCall") ? params.get("toolCall") : params.get("tool_call");
            String title = tc.has("title") ? tc.get("title").asText()
                    : tc.has("name") ? tc.get("name").asText() : "tool";

            String context = ToolContextExtractor.extractToolContext(tc);
            if (context != null) {
                prompt = NbBundle.getMessage(PermissionDialogManager.class, "MSG_PermissionToolWithContext", title, context);
            } else {
                prompt = NbBundle.getMessage(PermissionDialogManager.class, "MSG_PermissionTool", title);
            }
        }

        if (isDescendant && sessionControl != null) {
            String subAgentTitle = sessionControl.getCustomTitle(sessionId, null);
            if (subAgentTitle != null && !subAgentTitle.isEmpty()) {
                prompt = "[" + subAgentTitle + "] " + prompt;
            } else {
                prompt = "[Sub-Agent] " + prompt;
            }
        }

        final String finalPrompt = prompt;
        final JsonNode finalToolCall = params.has("toolCall") ? params.get("toolCall") 
                : (params.has("tool_call") ? params.get("tool_call") : null);
        SwingUtilities.invokeLater(() -> {
            chatPanel.addPermissionRequest(finalPrompt, params.get("options"), response, finalToolCall);
            activateCallback.run();
        });
    }
}
