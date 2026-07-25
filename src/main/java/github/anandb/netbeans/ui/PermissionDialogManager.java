package github.anandb.netbeans.ui;

import java.util.concurrent.CompletableFuture;

import javax.swing.SwingUtilities;

import org.openide.util.NbBundle;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ToolContextExtractor;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.SessionService;

// DSL-CONTROLLER: orchestrates permission request in the header panel + adds result to chat.
final class PermissionDialogManager {

    private static final Logger LOG = Logger.from(PermissionDialogManager.class);

    private final SessionService sessionService = PlatformBridge.sessionServiceSafe();

    private final ChatThreadPanel chatPanel;
    private final PermissionRequestPanel permissionPanel;

    PermissionDialogManager(ChatThreadPanel chatPanel, PermissionRequestPanel permissionPanel) {
        this.chatPanel = chatPanel;
        this.permissionPanel = permissionPanel;
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

        JsonNode toolCall = null;
        String prompt = NbBundle.getMessage(PermissionDialogManager.class, "MSG_PermissionRequested");
        if (params.has("message")) {
            prompt = params.get("message").asText();
        } else if (params.has("content")) {
            prompt = params.get("content").asText();
        } else if (params.has("toolCall") || params.has("tool_call")) {
            toolCall = params.has("toolCall") ? params.get("toolCall") : params.get("tool_call");
            // Use kind as tool name (e.g. "edit", "write") when title is a file path
            String title;
            if (toolCall.has("kind") && !toolCall.get("kind").asText().isEmpty()) {
                title = toolCall.get("kind").asText();
            } else {
                title = toolCall.has("title") ? toolCall.get("title").asText()
                        : toolCall.has("name") ? toolCall.get("name").asText() : "tool";
            }

            String context = ToolContextExtractor.extractToolContext(toolCall);
            // Avoid duplication: when title equals file path, don't show both
            if (context != null && !context.equals(title)
                    && !context.equals(toolCall.has("title") ? toolCall.get("title").asText() : null)) {
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
        final JsonNode finalToolCall = toolCall;
        SwingUtilities.invokeLater(() -> {
            permissionPanel.showRequest(finalPrompt, params.get("options"), response, finalToolCall);
            activateCallback.run();
        });
    }

    /** Called when a permission result is ready, adds the result to the chat thread. */
    void addResultToChat(String statusText, boolean allowed) {
        chatPanel.addPermissionResult(statusText, allowed);
    }
}
