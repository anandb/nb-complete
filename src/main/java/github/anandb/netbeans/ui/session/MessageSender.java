package github.anandb.netbeans.ui.session;

import org.apache.commons.lang3.exception.ExceptionUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.support.ToolDataExtractor;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.AttachedFile;
import github.anandb.netbeans.support.Logger;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.model.Harness;
import github.anandb.netbeans.contract.RpcFailure;
import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.SteerEchoes;
import github.anandb.netbeans.support.InterjectSupport;
import github.anandb.netbeans.support.SteeringRequest;
import github.anandb.netbeans.support.UsageTurnDedup;
import github.anandb.netbeans.support.VcsUtils;
import org.openide.util.RequestProcessor;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.chat.ChatThreadPanel;
import github.anandb.netbeans.ui.composer.AttachmentManager;
import github.anandb.netbeans.ui.composer.MessageHistory;
import github.anandb.netbeans.ui.composer.PlaceholderTextArea;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.SessionService;
import github.anandb.netbeans.ui.platform.ProcessService;
import github.anandb.netbeans.ui.editor.EditorContextCapture;

/**
 * Handles sending user messages and stopping message processing.
 * Manages slash command interception, file attachments, local echo, editor context,
 * and the async RPC call for message delivery.
 */
// DSL-CONTROLLER: not a view — MessageSender drives the send pipeline (input
// area trim, attachment attach, status callbacks, POST to ACP). Stays
// imperative.
public class MessageSender {

    private static final Logger LOG = Logger.from(MessageSender.class);

    /** Auto-backup runs off the EDT, serialized on its own processor. */
    private static final RequestProcessor BACKUP_RP = new RequestProcessor("ACP-AutoBackup", 1);

    private final SessionService sessionService;
    private final ProcessService processService;

    private final PlaceholderTextArea inputArea;
    private final ChatThreadPanel chatPanel;
    private final AttachmentManager attachmentManager;
    private final MessageHistory messageHistory;
    private final StatusController statusController;
    private final Runnable paperclipUpdater;
    private final Runnable inputFocusRequester;
    private Runnable onNewMessageCallback;
    private Runnable onMessageDoneCallback;
    private Runnable onUserMessageSentCallback;
    private Runnable onBeforeServerSendCallback;
    private BooleanSupplier permissionPendingCheck;
    private Runnable onPermissionBlockedCallback;
    private MessageQueueManager queueManager;
    private Supplier<Boolean> onTurnEndedCallback;
    private BooleanSupplier turnEndedCheck;
    private final AtomicInteger sendGeneration = new AtomicInteger();

    public MessageSender(
            PlaceholderTextArea inputArea,
            ChatThreadPanel chatPanel,
            AttachmentManager attachmentManager,
            MessageHistory messageHistory,
            StatusController statusController,
            Runnable paperclipUpdater,
            Runnable inputFocusRequester) {
        this.inputArea = inputArea;
        this.chatPanel = chatPanel;
        this.attachmentManager = attachmentManager;
        this.messageHistory = messageHistory;
        this.statusController = statusController;
        this.paperclipUpdater = paperclipUpdater;
        this.inputFocusRequester = inputFocusRequester;
        PlatformBridge bridge = PlatformBridge.get();
        if (bridge == null) {
            LOG.severe("PlatformBridge not found in Lookup — MessageSender services unavailable");
            this.sessionService = null;
            this.processService = null;
        } else {
            this.sessionService = bridge.sessionService();
            this.processService = bridge.processService();
        }
    }

    public void setOnNewMessageCallback(Runnable callback) {
        this.onNewMessageCallback = callback;
    }

    public void setOnMessageDoneCallback(Runnable callback) {
        this.onMessageDoneCallback = callback;
    }

    public void setOnUserMessageSentCallback(Runnable callback) {
        this.onUserMessageSentCallback = callback;
    }

    /** Invoked immediately before the message is actually sent to the server. Used to
     *  advance the permission epoch so stale permission requests are dropped. */
    public void setOnBeforeServerSendCallback(Runnable callback) {
        this.onBeforeServerSendCallback = callback;
    }

    /** Predicate that reports whether a permission request is awaiting a decision. When
     *  true, the send is blocked so the new message cannot supersede the pending request. */
    public void setPermissionPendingCheck(BooleanSupplier check) {
        this.permissionPendingCheck = check;
    }

    /** Invoked when the send is blocked because a permission request is pending. Draws
     *  attention to the pending request (e.g. buzzes the panel). */
    public void setOnPermissionBlockedCallback(Runnable callback) {
        this.onPermissionBlockedCallback = callback;
    }

    /** Reads the pending-permission state live: it can change while the interceptor runs. */
    private boolean isPermissionPending() {
        return permissionPendingCheck != null && permissionPendingCheck.getAsBoolean();
    }

    public void setQueueManager(MessageQueueManager queueManager) {
        this.queueManager = queueManager;
    }

    public void setOnTurnEndedCallback(Supplier<Boolean> callback) {
        this.onTurnEndedCallback = callback;
    }

    /** Provides a check for whether the current turn has ended.
     *  Used by the queue guard to detect active processing. */
    public void setTurnEndedCheck(BooleanSupplier check) {
        this.turnEndedCheck = check;
    }

    /** Sends (or intercepts) the current message text. */
    public void sendMessage() {
        sendMessage(inputArea.getText(), false);
    }

    /** Sends a command without placing it in or clearing the user's composer draft. */
    public void sendCompactCommand() {
        sendMessage("/compact", true);
    }

    private void sendMessage(String text, boolean preserveComposer) {
        if (!verifyPlatformServices()) {
            return;
        }

        // The new-session config gate keeps the composer shut until Continue: anything sent
        // now would reach the server before the preamble and be rejected as a second prompt.
        if (statusController.isComposerLocked()) {
            return;
        }

        // No session means nothing can go anywhere: bail out before the history, the slash
        // interceptor, the attachments or the queue are touched.
        String sessionId = sessionService.get().getCurrentSessionId();
        if (sessionId == null) {
            statusController.setStatus("STATUS_NoSession");
            return;
        }

        if (text.isEmpty() && attachmentManager.getAttachments().isEmpty()) {
            return;
        }
        // Add to history before slash intercept so local commands (/models, /title etc.)
        // are available via Alt+Up browsing.
        messageHistory.add(text);

        SlashRouting slash = interceptSlashCommand(text);
        if (slash.handled()) {
            if (!preserveComposer) {
                inputArea.setText("");
            }
            return;
        }

        ProcessControl proc = processService.get();

        // Mid-turn delivery has four routes: a harness with its own steering extension (goose)
        // adds the text to the running turn; Hermes steers through /steer; an agent that
        // advertises `_session/steering` injects it; a queueing harness holds it until turn end;
        // the rest accept a second prompt.
        boolean turnEnded = turnEndedCheck != null ? turnEndedCheck.getAsBoolean() : true;
        boolean midTurn = isMidTurn(turnEnded);

        if (midTurn && !slash.forwarded() && !isPermissionPending() && trySteer(text, proc)) {
            return;
        }
        if (midTurn && queueForTurnEnd(text, proc, preserveComposer)) {
            return;
        }
        if (blockedByPendingPermission()) {
            return;
        }

        dispatchPrompt(sessionId, text, slash, midTurn, turnEnded, preserveComposer);
    }


    /** Outcome of slash interception: a handled local command ends the send, while a
     *  forwarded one (`/compact`, `/dcp`) continues as a real send carrying its echo label. */
    private record SlashRouting(boolean handled, String forwardedEcho) {
        /** A forwarded command (`/compact`, `/dcp`) is a real send, not a local UI action. */
        boolean forwarded() {
            return forwardedEcho != null;
        }
    }

    /**
     * Intercepts local slash commands before any mid-turn routing: they are UI actions, so
     * they stay immediate instead of being steered or queued. Only the interception is
     * early — the echo of a forwarded command waits until the send is accepted, so a
     * blocked /compact leaves neither a bubble nor a cleared input.
     */
    private SlashRouting interceptSlashCommand(String text) {
        if (!text.trim().startsWith("/")) {
            return new SlashRouting(false, null);
        }
        SlashCommandInterceptor interceptor = processService.get().getSlashCommandInterceptor();
        if (interceptor != null) {
            CompletableFuture<Boolean> handled = interceptor.intercept(text, Lookup.getDefault());
            if (handled != null && handled.isDone() && !handled.isCompletedExceptionally()
                    && Boolean.TRUE.equals(handled.join())) {
                inputArea.setText("");
                return new SlashRouting(true, null);
            }
        }
        return new SlashRouting(false, ToolDataExtractor.getLocalEchoText(text));
    }

    /** Holds the text until the current turn ends. A queueing harness cannot take a second
     *  prompt mid-turn, and a queued message cannot supersede a pending permission decision. */
    private boolean queueForTurnEnd(String text, ProcessControl proc, boolean preserveComposer) {
        Harness caps = proc == null ? null : proc.getCapabilities();
        if (proc == null || caps == null || !caps.requiresMessageQueue()) {
            return false;
        }
        if (!text.isEmpty() && queueManager != null) {
            queueManager.enqueue(text);
            // Bubble is NOT drawn here — it is drawn later in sendQueuedMessage(), when the
            // combined text is actually posted. This keeps the panel free of half-visible
            // queued echoes.
            if (!preserveComposer) {
                inputArea.setText("");
            }
            // Queued user sends drop attachments because messages merge into one prompt;
            // implicit commands leave the current composer attachments untouched.
            if (!preserveComposer) {
                attachmentManager.clear();
                paperclipUpdater.run();
            }
        }
        return true;
    }

    /** Blocks the send while a permission request awaits a decision: a new prompt would
     *  supersede the pending request and hang the server, which is still waiting on the
     *  tool-call decision. Keeps the text in the input and surfaces the request instead. */
    private boolean blockedByPendingPermission() {
        if (!isPermissionPending()) {
            return false;
        }
        if (onPermissionBlockedCallback != null) {
            onPermissionBlockedCallback.run();
        }
        return true;
    }

    /** Issues session/prompt for an accepted send and wires its completion handlers. */
    private void dispatchPrompt(String sessionId, String text, SlashRouting slash,
            boolean midTurn, boolean turnEnded, boolean preserveComposer) {
        ProcessControl sendProc = processService.get();
        // Accepted, so the forwarded command's own echo is safe to draw now.
        if (slash.forwardedEcho() != null) {
            drawSlashEcho(slash.forwardedEcho());
        }
        beginSending(preserveComposer);
        PromptPayload payload = buildPromptPayload(sendProc, text, slash);
        beginTurn(turnEnded, !isCompactCommand(text));
        sendPrompt(sendProc, sessionId, text, payload, midTurn, preserveComposer);
    }

    /** Switches the panel into Sending/Stop and arms the stall watchdog. */
    private void beginSending(boolean preserveComposer) {
        if (!preserveComposer) {
            inputArea.setText("");
        }
        statusController.setStatus("STATUS_Sending");
        statusController.startThinking();
        statusController.updateButtonState(true);
        statusController.armRunWatchdog();
    }

    /** The prompt pieces assembled before the RPC. {@code attachedFiles} is kept so a failed
     *  send can put the files back in the composer — the payload cleared them. */
    private record PromptPayload(List<Map<String, Object>> fileBlocks,
            String clientMessageId, List<AttachedFile> attachedFiles) {
    }

    /** Builds the file blocks, drops the attachments and draws the local echo. */
    private PromptPayload buildPromptPayload(ProcessControl sendProc, String text, SlashRouting slash) {
        boolean isForwardedSlash = slash.forwardedEcho() != null;
        // The block shape depends on the handshake capability, so the blocks are built once
        // here and the echo uses the attachment list, not the blocks.
        boolean embeddedContext = sendProc != null && sendProc.supportsEmbeddedContext();
        List<AttachedFile> attachedFiles = attachmentManager.getAttachments();
        List<Map<String, Object>> fileBlocks = isForwardedSlash
                ? List.of() : attachmentManager.buildFileBlocks(embeddedContext);
        // Client-generated correlation ID so the local echo bubble can be matched to the
        // server-assigned message ID when it is echoed back — and dropped again if the
        // send fails, which is why it travels with the payload.
        String clientMessageId = UUID.randomUUID().toString();
        if (!isForwardedSlash) {
            attachmentManager.clear();
            paperclipUpdater.run();
            if (PluginSettings.isEchoUserInput()) {
                drawLocalEcho(text, attachedFiles, clientMessageId);
            }
        }
        if (onUserMessageSentCallback != null) {
            onUserMessageSentCallback.run();
        }
        return new PromptPayload(fileBlocks, clientMessageId,
                isForwardedSlash ? List.of() : attachedFiles);
    }

    /** Turn-start bookkeeping: the auto-backup and the permission epoch. */
    private void beginTurn(boolean turnEnded, boolean shouldBackupChanges) {
        if (shouldBackupChanges) {
            backupUncommittedChanges();
        }
        // Advance the permission epoch only when starting a new turn. Mid-turn
        // steering sends (OpenCode v1) must NOT advance: a permission request
        // captured on the reader thread between two steering sends would be
        // rejected as stale because the second send bumped the epoch.
        if (turnEnded && onBeforeServerSendCallback != null) {
            onBeforeServerSendCallback.run();
        }
    }

    private boolean isCompactCommand(String text) {
        String command = text.trim();
        if (!command.regionMatches(true, 0, "/compact", 0, 8)) {
            return false;
        }
        return command.length() == 8 || Character.isWhitespace(command.charAt(8));
    }

    /** Issues the prompt and routes its completion and failure back to the EDT handlers. */
    private void sendPrompt(ProcessControl sendProc, String sessionId, String text,
            PromptPayload payload, boolean midTurn, boolean preserveComposer) {
        // Editor Context — never for a slash command: the harness interprets the command
        // itself and an attached context breaks it (devin). Only plain prompts carry it,
        // and only when the agent injects context into prompts at all.
        Harness caps = sendProc == null ? null : sendProc.getCapabilities();
        boolean injectsContext = caps != null && caps.injectsEditorContext();
        boolean slashCommand = text.trim().startsWith("/");
        Map<String, Object> context = (slashCommand || !injectsContext)
                ? null : EditorContextCapture.capture();
        // Only after we actually issue session/prompt: local slash / no-session
        // returns must not set userPromptInFlight or skip preamble/reconnect done.
        if (onNewMessageCallback != null) {
            onNewMessageCallback.run();
        }
        // A mid-turn send only adds input to the running turn: its failure must not
        // end the turn UI (see onPromptFailed).
        final boolean steeringSend = midTurn;
        final int prevGen = sendGeneration.get();
        final int gen = sendGeneration.incrementAndGet();
        sendProc.sendMessage(sessionId, text, context, payload.fileBlocks())
                .thenAccept(result -> {
                    capturePromptUsage(sessionId, result);
                    SwingUtilities.invokeLater(() -> onPromptCompleted(gen, result));
                })
                .exceptionally(ex -> {
                    String restoreText = preserveComposer ? "" : text;
                    SwingUtilities.invokeLater(() -> onPromptFailed(
                            gen, prevGen, steeringSend, restoreText, ex, payload));
                    return null;
                });
    }


    // CPD-OFF — structural twin of sendQueuedMessage(); differences are per-method.
    /** Turn-end path for an accepted prompt, on the EDT. Ignores completions from a superseded send. */
    private void onPromptCompleted(int gen, JsonNode result) {
        if (gen != sendGeneration.get()) {
            LOG.info("Ignoring stale session/prompt completion gen={0} current={1}",
                    gen, sendGeneration.get());
            return;
        }
        LOG.info("RPC thenAccept fired (status during = {0}, hasStopReason = {1})",
                statusController.getStatusText(), result != null && result.has("stopReason"));
        if (onMessageDoneCallback != null) {
            onMessageDoneCallback.run();
        }
        // Always show Ready/Go. If messages were flushed,
        // a delayed timer will switch back to Sending/Stop.
        statusController.updateButtonState(false);
        statusController.stopThinking();
        statusController.setStatus("STATUS_Ready");
        statusController.disarmRunWatchdog();
        if (onTurnEndedCallback != null) {
            onTurnEndedCallback.get();
        }
        inputFocusRequester.run();

        // Handle turn completion from RPC result
        if (result != null && result.has("stopReason")) {
            LOG.info("Turn finished via RPC result: stopReason={0}", result.get("stopReason").asText());
            // Reset the debounced flush timer (it's also reset on every processed message via
            // drainMessageQueue), so the bubble is finalized 300ms after ALL messages drain —
            // even if the RPC result arrives before the last SSE delta.
            chatPanel.restartFlushTimer();
            showTruncationNotice(result);
        }
    }


    /**
     * Text for a failed prompt. goose classifies its failures in {@code error.data}: an
     * {@code auth_required} code or a {@code credits_exhausted} reason tells the user what to do,
     * where the generic message only repeats the server text. grok answers an exhausted quota
     * with {@code -32003} and puts the actionable detail ("used all the included free usage …
     * resets over a rolling 24-hour window") in {@code data.message}.
     */
    private static String promptFailureText(Throwable ex, String reason) {
        RpcFailure failure = RpcFailure.find(ex);
        if (failure == null) {
            return NbBundle.getMessage(UIBundle.class, "STATUS_Error", reason);
        }
        if (failure.code() == RpcFailure.AUTH_REQUIRED) {
            return NbBundle.getMessage(UIBundle.class, "MSG_AuthRequired", reason);
        }
        if (failure.code() == RpcFailure.RATE_LIMITED) {
            String detail = failure.dataField("message");
            return NbBundle.getMessage(UIBundle.class, "MSG_RateLimited",
                    detail != null && !detail.isBlank() ? detail : reason);
        }
        if (RpcFailure.REASON_CREDITS_EXHAUSTED.equals(failure.dataField("reason"))) {
            String url = failure.dataField("url");
            return url == null || url.isBlank()
                    ? NbBundle.getMessage(UIBundle.class, "MSG_CreditsExhausted", reason)
                    : NbBundle.getMessage(UIBundle.class, "MSG_CreditsExhaustedWithUrl", reason, url);
        }
        return NbBundle.getMessage(UIBundle.class, "STATUS_Error", reason);
    }

    /**
     * {@code stopReason=max_tokens} means the reply is incomplete. goose reports it for exactly
     * the condition behind {@code _meta.goose.outputTokenLimitReached}, so this one check covers
     * both the partial-reply and the fallback-content case, which used to be log-only.
     */
    private void showTruncationNotice(JsonNode result) {
        JsonNode reason = result != null ? result.get("stopReason") : null;
        if (reason == null || !"max_tokens".equals(reason.asText())) {
            return;
        }
        chatPanel.addMessage(ProcessedMessage.createInfo(
                NbBundle.getMessage(UIBundle.class, "INFO_ResponseTruncated")));
    }

    /** Failure path for an accepted prompt, on the EDT. A failed mid-turn send must not end the
     *  turn UI: the harness's first turn is still running, so Stop, the stall watchdog and the
     *  streaming bubble stay up and the turn-end claim returns to the original prompt. */
    private void onPromptFailed(int gen, int prevGen, boolean steeringSend, String messageText,
            Throwable ex, PromptPayload payload) {
        if (gen != sendGeneration.get()) {
            LOG.info("Ignoring stale session/prompt error gen={0} current={1}",
                    gen, sendGeneration.get());
            return;
        }
        LOG.info("RPC exceptionally fired: {0}", ExceptionUtils.getMessage(ex));
        // The echo bubble was never delivered: drop it so resending the restored text does
        // not draw a second identical bubble (user bubbles never merge).
        chatPanel.removeMessageBubble(payload.clientMessageId());
        String reason = ExceptionUtils.getMessage(ex) != null
                ? ExceptionUtils.getMessage(ex) : ex.getClass().getSimpleName();
        statusController.setStatus("STATUS_Error", reason);
        chatPanel.addMessage(ProcessedMessage.createError(
                MessageType.error_response,
                promptFailureText(ex, reason),
                null, null
        ));
        if (steeringSend && isMidTurn()) {
            // The harness's first turn is still running: this send only added input to it,
            // so the failure must not end the turn UI. Restore the text, hand the turn-end
            // claim back to the original prompt and keep the watchdog ticking — without it
            // the Stop button would stay up with nothing watching the run.
            restoreComposerText(messageText);
            restoreAttachments(payload.attachedFiles());
            sendGeneration.compareAndSet(gen, prevGen);
            statusController.setInputEnabled(true);
            statusController.updateButtonState(true);
            if (!statusController.isRunWatchdogArmed()) {
                statusController.armRunWatchdog();
            }
            statusController.touchRunActivity();
            inputFocusRequester.run();
            return;
        }
        restoreComposerText(messageText);
        restoreAttachments(payload.attachedFiles());
        statusController.stopThinking();
        statusController.disarmRunWatchdog();
        chatPanel.stopStreaming();
        statusController.setInputEnabled(true);
        statusController.updateButtonState(false);
        if (onMessageDoneCallback != null) {
            onMessageDoneCallback.run();
        }
        if (onTurnEndedCallback != null) {
            onTurnEndedCallback.get();
        }
        inputFocusRequester.run();
    }
    // CPD-ON

    /** Re-reads whether a turn is still in flight. A mid-turn send's failure may only keep
     *  the turn UI alive while that turn is really running: the turn can end between the
     *  routing decision and the RPC failure. */
    private boolean isMidTurn() {
        return isMidTurn(turnEndedCheck == null || turnEndedCheck.getAsBoolean());
    }

    /** The mid-turn test over an already-read turn state ({@code !canSend || !turnEnded}). */
    private boolean isMidTurn(boolean turnEnded) {
        return !turnEnded || !sessionService.get().canSendMessage();
    }

    /** Puts a failed send's attachments back in the composer. {@link AttachmentManager#add}
     *  enforces the caps, so files attached during the flight can leave no room — a dropped
     *  file must be picked again, so the notice wins the label over the failure text (which
     *  is logged, and shown as an error bubble on the prompt path). */
    private void restoreAttachments(List<AttachedFile> attachedFiles) {
        if (attachedFiles == null || attachedFiles.isEmpty()) {
            return;
        }
        int restored = attachmentManager.restore(attachedFiles);
        paperclipUpdater.run();
        if (restored < attachedFiles.size()) {
            statusController.setStatus("STATUS_AttachmentsDropped", attachedFiles.size() - restored);
            statusController.scheduleReset();
        }
    }

    /** Puts a failed send's text back in the composer, unless the user typed something while
     *  it was in flight: that text is newer, and the failed one is still in the Alt+Up history. */
    private void restoreComposerText(String messageText) {
        String typed = inputArea.getText();
        if (typed == null || typed.isEmpty()) {
            inputArea.setText(messageText);
        }
    }

    /** Draws the forwarded command's own echo once the send is accepted. */
    private void drawSlashEcho(String echoText) {
        chatPanel.addMessage(new ProcessedMessage.Builder()
                .messageType(MessageType.tool_call_update)
                .text(echoText)
                .messageId(echoText)
                .kind("Slash Command")
                .toolTitle(echoText)
                .rawText(echoText)
                .build());
    }

    /** Draws the user bubble for a normal send, one label line per attached file. */
    private void drawLocalEcho(String text, List<AttachedFile> attachedFiles, String messageId) {
        String echoText = buildEchoText(text, attachedFiles);
        chatPanel.addMessage(new ProcessedMessage.Builder()
                .messageType(MessageType.user_message_chunk)
                .text(echoText)
                .rawText(echoText)
                .messageId(messageId)
                .build());
    }

    /** Backs up uncommitted VCS changes off the EDT before the agent touches the tree. */
    private void backupUncommittedChanges() {
        if (!PluginSettings.isAutoBackupChanges()) {
            return;
        }
        final String sessionDir = sessionService.get().getCurrentSessionDirectory();
        BACKUP_RP.post(() -> {
            try {
                VcsUtils.VcsInfo vcsInfo = VcsUtils.findVcsInfo(sessionDir);
                if (vcsInfo != null && VcsUtils.hasUncommittedChanges(vcsInfo)) {
                    String backupMsg = VcsUtils.backupUncommittedChanges(vcsInfo);
                    if (backupMsg != null) {
                        SwingUtilities.invokeLater(() -> chatPanel.addMessage(new ProcessedMessage.Builder()
                                .messageType(MessageType.tool_call_update)
                                .text(backupMsg)
                                .messageId(UUID.randomUUID().toString())
                                .kind("System Backup")
                                .toolTitle("Auto-Backup")
                                .rawText(backupMsg)
                                .build()));
                    }
                }
            } catch (Exception ex) {
                LOG.info("Auto-backup failed: {0}", ExceptionUtils.getMessage(ex));
            }
        });
    }


    /**
     * Adds the text to the running turn. Returns false when the harness cannot
     * steer right now, leaving the caller to queue or to start a new prompt.
     * The mechanisms are: a dedicated extension (goose), a slash command the
     * harness interprets as guidance (Hermes {@code /steer}), the cross-adapter
     * {@code _session/steering} extension the agent advertises, and Grok Build's
     * {@code x.ai/interject} extension.
     */
    private boolean trySteer(String text, ProcessControl proc) {
        if (proc == null) {
            return false;
        }
        Harness caps = proc.getCapabilities();
        if (caps != null && caps.supportsSessionSteer()) {
            return tryExtensionSteer(text, proc);
        }
        if (caps != null && caps.supportsSlashSteer()) {
            return trySlashSteer(text, proc);
        }
        if (proc.supportsSessionSteering()) {
            return tryProtocolSteer(text, proc);
        }
        if (caps != null && caps.supportsInterject() && InterjectSupport.isSupported()) {
            return tryInterject(text, proc);
        }
        return false;
    }

    /** Steers through the harness's own extension (goose `_goose/unstable/session/steer`). */
    private boolean tryExtensionSteer(String text, ProcessControl proc) {
        String sessionId = sessionService.get().getCurrentSessionId();
        if (sessionId == null || proc.getActiveRunId(sessionId) == null) {
            return false;
        }
        SteerDraft draft = prepareSteerDraft(text, proc);
        if (draft == null) {
            return false;
        }
        proc.steerMessage(sessionId, draft.echoText(), draft.fileBlocks())
                .thenAccept(result -> {
                    String messageId = result != null && result.has("messageId")
                            ? result.get("messageId").asText() : null;
                    // goose streams the queued steer back later; the local echo already
                    // drew it, so that echo is skipped by its server message id.
                    if (PluginSettings.isEchoUserInput()) {
                        SteerEchoes.expect(sessionId, messageId);
                    }
                    LOG.info("Steered running turn for session {0}, messageId={1}", sessionId, messageId);
                })
                .exceptionally(ex -> {
                    onSteerFailure(ex, draft);
                    return null;
                });
        return true;
    }

    /**
     * Steers through Hermes' {@code /steer} slash command. The harness answers the
     * steer prompt with an immediate {@code end_turn} while the original turn keeps
     * running, so this route deliberately does not touch the send generation or the
     * turn-end handlers — the original prompt's completion still ends the turn.
     */
    private boolean trySlashSteer(String text, ProcessControl proc) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || !attachmentManager.getAttachments().isEmpty()) {
            return false; // attachments cannot travel through a text-only slash command
        }
        if (trimmed.startsWith("/") && !trimmed.equals("/steer") && !trimmed.startsWith("/steer ")) {
            return false; // leave /queue, /compact, ... to their normal routing
        }
        String sessionId = sessionService.get().getCurrentSessionId();
        if (sessionId == null) {
            return false;
        }
        String steerCommand = trimmed.startsWith("/steer") ? text : "/steer " + text;
        inputArea.setText("");
        final String echoText = text;
        final String echoMessageId = UUID.randomUUID().toString();
        if (PluginSettings.isEchoUserInput()) {
            chatPanel.addMessage(new ProcessedMessage.Builder()
                    .messageType(MessageType.user_message_chunk)
                    .text(echoText)
                    .rawText(echoText)
                    .messageId(echoMessageId)
                    .build());
        }
        proc.sendMessage(sessionId, steerCommand, null, List.of())
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        statusController.setStatus("STATUS_Error",
                                ExceptionUtils.getMessage(ex) != null
                                        ? ExceptionUtils.getMessage(ex) : ex.getClass().getSimpleName());
                        restoreComposerText(echoText);
                        // The steer never landed: drop the echo so a resend does not
                        // draw a second identical bubble.
                        chatPanel.removeMessageBubble(echoMessageId);
                        inputFocusRequester.run();
                    });
                    return null;
                });
        return true;
    }

    /**
     * Steers through the cross-adapter {@code _session/steering} extension (claude-agent-acp and
     * several pi ACP adapters). It answers with an {@code outcome} instead of a stop reason, so
     * this route never touches the send generation or the turn-end handlers — the original
     * prompt's completion still ends the turn.
     */
    private boolean tryProtocolSteer(String text, ProcessControl proc) {
        String sessionId = sessionService.get().getCurrentSessionId();
        if (sessionId == null) {
            return false;
        }
        SteerDraft draft = prepareSteerDraft(text, proc);
        if (draft == null) {
            return false;
        }
        proc.steerSession(sessionId, draft.echoText(), draft.fileBlocks())
                .thenAccept(result -> {
                    if (SteeringRequest.isInjected(result)) {
                        // Adapters that stream the injected message back would draw it twice;
                        // the reply carries no message id, so the echo is matched by text.
                        if (PluginSettings.isEchoUserInput()) {
                            SteerEchoes.expectText(sessionId, draft.echoText());
                        }
                        LOG.info("Steered running turn for session {0} via {1}",
                                sessionId, SteeringRequest.METHOD);
                        return;
                    }
                    // `promptRequired`/`startedNewTurn`: the turn this was meant for is already
                    // gone, so hand the text back rather than drop it silently. Any other outcome
                    // (omp's `failed`) is a real delivery error and is surfaced as one.
                    boolean turnGone = SteeringRequest.isTurnGone(result);
                    String outcome = SteeringRequest.outcome(result);
                    SwingUtilities.invokeLater(() -> {
                        if (turnGone) {
                            statusController.setStatus("STATUS_SteerTurnEnded");
                        } else {
                            statusController.setStatus("STATUS_Error", outcome != null ? outcome : "no outcome");
                        }
                        restoreFailedSteer(draft);
                    });
                })
                .exceptionally(ex -> {
                    onSteerFailure(ex, draft);
                    return null;
                });
        return true;
    }

    /**
     * Interjects through Grok Build's {@code x.ai/interject} extension. The interjection joins
     * the running turn at the next safe point; live user echo is opt-in via
     * {@code x.ai/userMessageEcho}, which the plugin does not send, so the local echo is the only
     * renderer and no {@code SteerEchoes} expectation is registered.
     */
    private boolean tryInterject(String text, ProcessControl proc) {
        String sessionId = sessionService.get().getCurrentSessionId();
        if (sessionId == null) {
            return false;
        }
        SteerDraft draft = prepareSteerDraft(text, proc);
        if (draft == null) {
            return false;
        }
        proc.interjectMessage(sessionId, draft.echoText(), draft.fileBlocks())
                .thenAccept(result -> LOG.info("Interjected running turn for session {0}", sessionId))
                .exceptionally(ex -> {
                    onSteerFailure(ex, draft);
                    return null;
                });
        return true;
    }

    /** Attachment snapshot, cleared composer state and local echo shared by the steer routes. */
    private record SteerDraft(List<AttachedFile> attachedFiles,
            List<Map<String, Object>> fileBlocks, String echoText, String echoMessageId) {
    }

    /**
     * Builds the file blocks, clears the composer and draws the local echo. Returns null
     * when there is nothing to steer (no text and no attachments).
     */
    private SteerDraft prepareSteerDraft(String text, ProcessControl proc) {
        List<AttachedFile> attachedFiles = attachmentManager.getAttachments();
        if ((text == null || text.isEmpty()) && attachedFiles.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> fileBlocks = attachmentManager.buildFileBlocks(proc.supportsEmbeddedContext());
        attachmentManager.clear();
        paperclipUpdater.run();
        inputArea.setText("");
        String echoText = text == null ? "" : text;
        String echoMessageId = UUID.randomUUID().toString();
        if (PluginSettings.isEchoUserInput()) {
            drawLocalEcho(echoText, attachedFiles, echoMessageId);
        }
        return new SteerDraft(attachedFiles, fileBlocks, echoText, echoMessageId);
    }

    /** Reports a steer delivery error, then restores the text, echo bubble and attachments. */
    private void onSteerFailure(Throwable ex, SteerDraft draft) {
        SwingUtilities.invokeLater(() -> {
            String message = ExceptionUtils.getMessage(ex);
            statusController.setStatus("STATUS_Error",
                    message != null ? message : ex.getClass().getSimpleName());
            restoreFailedSteer(draft);
        });
    }

    /** Undoes a steer draft that never landed; the caller sets the status first. */
    private void restoreFailedSteer(SteerDraft draft) {
        restoreComposerText(draft.echoText());
        chatPanel.removeMessageBubble(draft.echoMessageId());
        restoreAttachments(draft.attachedFiles());
        inputFocusRequester.run();
    }

    /** Renders the local echo of a user message, appending one label line per attachment. */
    private static String buildEchoText(String text, List<AttachedFile> attachedFiles) {
        StringBuilder echoBuilder = new StringBuilder(text);
        if (!attachedFiles.isEmpty()) {
            if (!text.isBlank()) {
                echoBuilder.append("\n");
            }
            for (AttachedFile af : attachedFiles) {
                echoBuilder.append("\n[")
                        .append(af.isImage()
                                ? NbBundle.getMessage(UIBundle.class, "LBL_Image")
                                : NbBundle.getMessage(UIBundle.class, "LBL_File")).append(": ")
                        .append(af.filename()).append("]");
            }
        }
        return echoBuilder.toString();
    }

    /** Stops the currently processing message. */
    public void stopMessage() {
        // Only send the cancel notification when the state machine allows it,
        // but ALWAYS reset the UI below — Stop doubles as a manual recovery
        // when the toolbar is stuck disabled.
        try {
            if (sessionService != null && sessionService.get() != null && sessionService.get().canStopMessage()) {
                SwingUtilities.invokeLater(() -> {
                    statusController.setStatus("STATUS_Stopping");
                    statusController.startThinking();
                });
                // stopCurrentMessage may block on pipe I/O (writer.println).
                // Run off EDT to avoid freezing the UI.
                CompletableFuture.runAsync(() -> {
                    try {
                        sessionService.get().stopCurrentMessage();
                    } catch (Exception ex) {
                        LOG.info("Error stopping message: {0}", ExceptionUtils.getMessage(ex));
                    }
                });
            }
        } catch (Exception ex) {
            LOG.info("Error checking canStopMessage: {0}", ExceptionUtils.getMessage(ex));
        } finally {
            // Show "Stopped" immediately — don't wait for cancel notification to be sent.
            SwingUtilities.invokeLater(() -> {
                try {
                    statusController.setStatus("STATUS_Stopped");
                    statusController.stopThinking();
                    statusController.disarmRunWatchdog();
                    if (chatPanel != null) {
                        chatPanel.stopStreaming();
                    }
                } catch (Exception ex) {
                    LOG.info("Error during stop UI reset: {0}", ExceptionUtils.getMessage(ex));
                } finally {
                    if (statusController != null) {
                        statusController.updateButtonState(false);
                        statusController.setInputEnabled(true);
                    }
                    // Set turnEnded so late SSE displayMessage() doesn't re-enable processing.
                    if (onMessageDoneCallback != null) {
                        try {
                            onMessageDoneCallback.run();
                        } catch (Exception ex) {
                            LOG.info("Error running message done callback: {0}", ExceptionUtils.getMessage(ex));
                        }
                    }
                }
            });
        }
    }

    /**
     * Sends a combined queued message through the normal pipeline.
     * Called when the turn ends and there are queued messages waiting.
     * Skips the queue check and local echo (individual echoes were shown when queued).
     */
    public void sendQueuedMessage(String combinedText) {
        if (combinedText == null || combinedText.isEmpty()) {
            return;
        }
        if (sessionService == null || processService == null) {
            LOG.severe("sendQueuedMessage ignored — platform services unavailable (PlatformBridge missing from Lookup)");
            return;
        }
        String currentSessionId = sessionService.get().getCurrentSessionId();
        if (currentSessionId == null) {
            statusController.setStatus("STATUS_NoSession");
            return;
        }

        statusController.setStatus("STATUS_Sending");
        statusController.startThinking();
        statusController.updateButtonState(true);
        statusController.armRunWatchdog();

        // No attachments — they were cleared when queued.
        // Editor Context — gated by system property to disable auto-injection
        // Advance the permission epoch — consistent with sendMessage(). Guard
        // with turnEnded so the epoch is never bumped mid-turn (latent safety).
        boolean turnEnded = turnEndedCheck != null ? turnEndedCheck.getAsBoolean() : true;
        if (turnEnded && onBeforeServerSendCallback != null) {
            onBeforeServerSendCallback.run();
        }

        Map<String, Object> context = null;
        // Same gate as sendMessage(): in-flight is tied to the RPC, not UI prep.
        if (onNewMessageCallback != null) {
            onNewMessageCallback.run();
        }
        // Draw a single bubble for the combined queued text — replaces the
        // per-message bubbles that were previously shown while queuing.
        String clientMessageId = UUID.randomUUID().toString();
        chatPanel.addMessage(new ProcessedMessage.Builder()
                .messageType(MessageType.user_message_chunk)
                .text(combinedText)
                .rawText(combinedText)
                .messageId(clientMessageId)
                .build());
        final int gen = sendGeneration.incrementAndGet();
        processService.get().sendMessage(currentSessionId, combinedText, context, List.of())
                .thenAccept(result -> {
                    capturePromptUsage(currentSessionId, result);
                    // CPD-OFF — structural twin of sendMessage(); differences are
                    // per-method (no logging, combinedText).
                    SwingUtilities.invokeLater(() -> {
                        if (gen != sendGeneration.get()) {
                            LOG.info("Ignoring stale queued prompt completion gen={0} current={1}",
                                    gen, sendGeneration.get());
                            return;
                        }
                        statusController.updateButtonState(false);
                        statusController.stopThinking();
                        if (onMessageDoneCallback != null) {
                            onMessageDoneCallback.run();
                        }
                        statusController.setStatus("STATUS_Ready");
                        statusController.disarmRunWatchdog();
                        if (onTurnEndedCallback != null) {
                            onTurnEndedCallback.get();
                        }
                        inputFocusRequester.run();
                        if (result != null && result.has("stopReason")) {
                            chatPanel.restartFlushTimer();
                            showTruncationNotice(result);
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        if (gen != sendGeneration.get()) {
                            LOG.info("Ignoring stale queued prompt error gen={0} current={1}",
                                    gen, sendGeneration.get());
                            return;
                        }
                        statusController.setStatus("STATUS_Error",
                            ExceptionUtils.getMessage(ex) != null ? ExceptionUtils.getMessage(ex) : ex.getClass().getSimpleName());
                        statusController.stopThinking();
                        statusController.disarmRunWatchdog();
                        chatPanel.stopStreaming();
                        // The combined echo never reached the server: drop it so flushing the
                        // restored text again does not leave two identical user bubbles.
                        chatPanel.removeMessageBubble(clientMessageId);
                        String queuedReason = ExceptionUtils.getMessage(ex) != null
                                ? ExceptionUtils.getMessage(ex) : ex.getClass().getSimpleName();
                        chatPanel.addMessage(ProcessedMessage.createError(
                                MessageType.error_response,
                                promptFailureText(ex, queuedReason),
                                null, null
                        ));
                        restoreComposerText(combinedText);
                        statusController.setInputEnabled(true);
                        statusController.updateButtonState(false);
                        if (onMessageDoneCallback != null) {
                            onMessageDoneCallback.run();
                        }
                        inputFocusRequester.run();
                        if (onTurnEndedCallback != null) {
                            onTurnEndedCallback.get();
                        }
                    });
                    return null;
                });
        // CPD-ON
    }

    /**
     * Records the {@code usage} object of a {@code session/prompt} result as one
     * stats row (CAP-2). A result with no {@code usage} object writes no row.
     * Called on the RPC completion thread, never the EDT.
     */
    private void capturePromptUsage(String sessionId, JsonNode result) {
        if (result == null || sessionId == null) {
            return;
        }
        JsonNode meta = result.get("_meta");
        JsonNode usage = result.get("usage");
        if ((usage == null || !usage.isObject()) && meta != null) {
            usage = meta.get("usage");
        }
        if (usage == null || !usage.isObject()) {
            return;
        }
        // Grok reports the turn usage in the result _meta; the same turn also arrives
        // in an _x.ai/session_notification frame, so dedupe on the turn id.
        String turnId = meta != null ? meta.path("promptId").asText("") : "";
        if (!UsageTurnDedup.firstCapture(sessionId, turnId)) {
            return;
        }
        UsageStatsStore store = PlatformBridge.serviceSafe(UsageStatsStore.class);
        if (store == null) {
            return;
        }
        SessionQuery sessions = PlatformBridge.serviceSafe(SessionQuery.class);
        Attribution attribution = new Attribution(sessionId,
                sessions != null ? sessions.getHarnessId() : null,
                sessions != null ? sessions.getSessionModelId(sessionId) : null,
                sessions != null ? sessions.getSessionDirectory(sessionId) : null);
        long input = usage.path("inputTokens").asLong(0);
        long output = usage.path("outputTokens").asLong(0);
        long total = usage.hasNonNull("totalTokens") ? usage.get("totalTokens").asLong() : input + output;
        Long thought = usage.hasNonNull("thoughtTokens") ? usage.get("thoughtTokens").asLong()
                : (usage.hasNonNull("reasoningTokens") ? usage.get("reasoningTokens").asLong() : null);
        store.recordPromptUsage(new PromptUsageRow(attribution, System.currentTimeMillis(),
                input, output, total, thought,
                usage.hasNonNull("cachedReadTokens") ? usage.get("cachedReadTokens").asLong() : null));
    }

    private boolean verifyPlatformServices() {
        if (sessionService == null || processService == null) {
            LOG.severe("sendMessage ignored — platform services unavailable (PlatformBridge missing from Lookup)");
            statusController.setStatus("STATUS_Error", "Assistant services unavailable");
            return false;
        }

        return true;
    }
}
