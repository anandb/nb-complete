package github.anandb.netbeans.ui;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

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
    private static final Pattern WS_PATTERN = Pattern.compile("\\s+");

    /** Max chars of an execute command shown in the permission dialog title/context. */
    private static final int COMMAND_DISPLAY_MAX = 60;

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

    /** Shakes the sidebar permission panel to draw attention to the pending request.
     *  If the panel was dismissed without being answered (missed dialog), re-surface it
     *  and bring the assistant to the front so the user can actually decide. This is the
     *  only way to get back a permission request whose dialog was missed — otherwise the
     *  server stays blocked on the tool-call decision until an idle timeout. */
    void buzzPermissionPanel() {
        // Resurface whenever a request is still active, regardless of isVisible():
        // the panel can be stuck in a visible-but-zero-height state (killed slide
        // animation) where isVisible() is true but nothing is shown — gating on
        // !isVisible() made recovery impossible in exactly the broken state.
        if (permissionPanel.isRequestActive()) {
            permissionPanel.resurface();
        }
        permissionPanel.buzz();
        AssistantTopComponent top = AssistantTopComponent.findInstance();
        if (top != null) {
            top.requestActive();
            top.toFront();
        }
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
            String title = resolveToolTitle(toolCall);

            String context = ToolContextExtractor.extractToolContext(toolCall, Integer.MAX_VALUE);

            // Execute tool calls use the raw command as the title. A long compound
            // command (e.g. "git add … && git commit -m …") would flood the dialog —
            // cap both title and context to a short prefix.
            boolean isExecute = toolCall.has("kind") && "execute".equals(toolCall.get("kind").asText());
            if (isExecute) {
                title = ToolContextExtractor.truncateCommand(title, COMMAND_DISPLAY_MAX);
                if (context != null) {
                    context = ToolContextExtractor.truncateCommand(context, COMMAND_DISPLAY_MAX);
                }
            }

            // Drop the context when it restates the title (e.g. the command shown
            // twice) — whitespace-insensitive so formatting differences don't defeat it.
            if (context != null && normalizeWhitespace(context).equals(normalizeWhitespace(title))) {
                context = null;
            }

            if (context != null) {
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
                permissionPanel.showRequest(finalPrompt, params.get("options"),
                        response, finalToolCall);
                MiniAssistantDialog miniDialog = MiniAssistantDialog.getInstance();
                if (miniDialog != null) {
                    miniDialog.showPermissionRequest(
                        finalPrompt, params.get("options"), response, finalToolCall,
                        permissionPanel.getCurrentFileChanges()
                    );
                    // File changes load asynchronously in the full panel; refresh
                    // the mini panel's Show Diff button once they are ready.
                    CompletableFuture<List<FileChange>> changesFuture =
                        permissionPanel.getFileChangesFuture();
                    if (changesFuture != null) {
                        changesFuture.thenAcceptAsync(
                                miniDialog::refreshPermissionDiffButton,
                                SwingUtilities::invokeLater);
                    }
                }

                response.whenComplete((res, err) -> {
                    SwingUtilities.invokeLater(() -> {
                        LOG.info("Permission response completed: res={0}, err={1}",
                                new Object[] { res, err == null ? "none" : err.toString() });
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

    /**
     * Prefers a human title ("Write /path") over a bare kind ("edit").
     * Uses kind only when the title is a raw file path with no spaces.
     */
    private static String resolveToolTitle(JsonNode toolCall) {
        String kind = null;
        if (toolCall.has("kind") && !toolCall.get("kind").asText().isEmpty()) {
            kind = toolCall.get("kind").asText();
        }
        String named = null;
        if (toolCall.has("title") && !toolCall.get("title").asText().isEmpty()) {
            named = toolCall.get("title").asText();
        } else if (toolCall.has("name") && !toolCall.get("name").asText().isEmpty()) {
            named = toolCall.get("name").asText();
        }
        if (named == null) {
            return kind != null ? kind : "tool";
        }
        boolean titleIsPath = !named.contains(" ")
                && (named.contains("/") || named.contains("\\"));
        if (titleIsPath && kind != null) {
            return kind;
        }
        return named;
    }

    /** Collapses all whitespace (incl. newlines) to single spaces and trims,
     *  so trivially-different renderings of the same command compare equal. */
    private static String normalizeWhitespace(String s) {
        return s == null ? "" : WS_PATTERN.matcher(s).replaceAll(" ").trim();
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
