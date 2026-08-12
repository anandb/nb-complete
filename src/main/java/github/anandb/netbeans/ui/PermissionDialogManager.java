package github.anandb.netbeans.ui;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.SwingUtilities;

import org.openide.util.NbBundle;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ToolCallDiffParser.FileChange;
import github.anandb.netbeans.support.ToolContextExtractor;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.SessionService;

// DSL-CONTROLLER: orchestrates permission request in the header panel + adds result to chat.
final class PermissionDialogManager {

    private static final Logger LOG = Logger.from(PermissionDialogManager.class);

    private final SessionService sessionService = PlatformBridge.sessionServiceSafe();

    private final ChatThreadPanel chatPanel;
    private final PermissionRequestPanel permissionPanel;

    record PendingRequest(Runnable task, CompletableFuture<String> response) {}
    private final Queue<PendingRequest> requestQueue = new LinkedList<>();
    private boolean isRequestShowing = false;

    /** Advances whenever a new user message is sent; used to reject stale permission
     *  requests that belong to a turn already superseded by a newer message. */
    private final AtomicLong permissionEpoch = new AtomicLong(0);

    PermissionDialogManager(ChatThreadPanel chatPanel, PermissionRequestPanel permissionPanel) {
        this.chatPanel = chatPanel;
        this.permissionPanel = permissionPanel;
    }

    long currentEpoch() {
        return permissionEpoch.get();
    }

    /** Invoked immediately before a new user message is actually sent to the server.
     *  Advances the epoch so permission requests still in flight from a turn that was
     *  superseded by the new message are dropped rather than displayed. */
    void recordUserMessageSent() {
        permissionEpoch.incrementAndGet();
    }

    /** True while a permission request is awaiting a user decision. While pending, the
     *  input send is blocked so a new message cannot supersede the outstanding request. */
    boolean isPermissionPending() {
        return isRequestShowing;
    }

    /** Shakes the sidebar permission panel to draw attention to the pending request. */
    void buzzPermissionPanel() {
        permissionPanel.buzz();
    }

    void handlePermissionRequest(String sessionId, JsonNode params,
            CompletableFuture<String> response, Runnable activateCallback) {
        // Session check runs on any thread — safe to reject unrelated
        // sessions immediately without hopping to EDT.
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

        // The remaining work accesses requestQueue (non-thread-safe) and UI,
        // so it must run on EDT.
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() ->
                handlePermissionRequest(sessionId, params, response, activateCallback));
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

            String context = ToolContextExtractor.extractToolContext(toolCall, Integer.MAX_VALUE);
            if (context != null && !context.equals(title)) {
                prompt = NbBundle.getMessage(PermissionDialogManager.class, "MSG_PermissionToolWithContext", title, "");
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
        
        Runnable showTask = () -> {
            try {
                permissionPanel.showRequest(finalPrompt, params.get("options"), response, finalToolCall);
                MiniAssistantDialog miniDialog = MiniAssistantDialog.getInstance();
                if (miniDialog != null) {
                    miniDialog.showPermissionRequest(
                        finalPrompt, params.get("options"), response, finalToolCall,
                        permissionPanel.getCurrentFileChanges()
                    );
                    // File changes load asynchronously in the full panel; refresh the
                    // mini panel's Show Diff button once they are ready.
                    CompletableFuture<List<FileChange>> changesFuture =
                        permissionPanel.getFileChangesFuture();
                    if (changesFuture != null) {
                        changesFuture.thenAcceptAsync(miniDialog::refreshPermissionDiffButton,
                            SwingUtilities::invokeLater);
                    }
                }
                
                response.whenComplete((res, err) -> {
                    SwingUtilities.invokeLater(() -> {
                        permissionPanel.slideClose();
                        if (miniDialog != null) {
                            miniDialog.hidePermissionRequest();
                        }
                        processNextRequest();
                    });
                });
                
                boolean miniDialogShowing = miniDialog != null && miniDialog.isShowing();
                if (!miniDialogShowing) {
                    activateCallback.run();
                }
            } catch (Exception e) {
                LOG.severe("Failed to show permission request", e);
                if (!response.isDone()) {
                    response.complete("reject");
                }
                processNextRequest();
            }
        };

        // Caller is already on EDT (scheduled by AcpRequestRouter via invokeLater).
        // Avoid a redundant invokeLater that would queue behind streaming updates
        // and delay the permission panel appearance.
        requestQueue.offer(new PendingRequest(showTask, response));
        if (!isRequestShowing) {
            processNextRequest();
        }
    }

    private void processNextRequest() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::processNextRequest);
            return;
        }
        PendingRequest next = requestQueue.poll();
        if (next != null) {
            isRequestShowing = true;
            next.task().run();
        } else {
            isRequestShowing = false;
        }
    }

    /** Called when a permission result is ready, adds the result to the chat thread. */
    void addResultToChat(String statusText, boolean allowed) {
        chatPanel.addPermissionResult(statusText, allowed);
    }

    /** Rejects all pending requests in the queue and the currently active one. */
    void rejectAllRequests() {
        permissionPanel.rejectRequest();
        MiniAssistantDialog miniDialog = MiniAssistantDialog.getInstance();
        if (miniDialog != null) {
            miniDialog.hidePermissionRequest();
        }
        SwingUtilities.invokeLater(() -> {
            PendingRequest req;
            while ((req = requestQueue.poll()) != null) {
                if (!req.response().isDone()) {
                    req.response().complete("reject");
                }
            }
        });
    }
}
