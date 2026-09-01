package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import javax.swing.SwingUtilities;

import java.util.concurrent.CompletableFuture;

import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.support.ToolDataExtractor;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.support.Logger;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.NbBundle;

import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProcessService;
import github.anandb.netbeans.ui.platform.SessionService;
import java.util.prefs.PreferenceChangeListener;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.VcsUtils;
import org.openide.util.RequestProcessor;

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

    private static volatile boolean localEchoEnabled = true;
    private static final PreferenceChangeListener PREF_LISTENER = evt -> {
        if ("echoUserInput".equals(evt.getKey())) {
            localEchoEnabled = Boolean.parseBoolean(evt.getNewValue());
        }
    };
    static {
        localEchoEnabled = NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("echoUserInput", true);
        NbPreferences.forModule(ACPOptionsPanel.class).addPreferenceChangeListener(PREF_LISTENER);
    }

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
    private java.util.function.Supplier<Boolean> onTurnEndedCallback;
    private BooleanSupplier turnEndedCheck;

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
        PlatformBridge bridge = Lookup.getDefault().lookup(PlatformBridge.class);
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

    public void setQueueManager(MessageQueueManager queueManager) {
        this.queueManager = queueManager;
    }

    public void setOnTurnEndedCallback(java.util.function.Supplier<Boolean> callback) {
        this.onTurnEndedCallback = callback;
    }

    /** Provides a check for whether the current turn has ended.
     *  Used by the queue guard to detect active processing. */
    public void setTurnEndedCheck(BooleanSupplier check) {
        this.turnEndedCheck = check;
    }

    /** Sends (or intercepts) the current message text. */
    public void sendMessage() {
        boolean turnEnded = turnEndedCheck != null ? turnEndedCheck.getAsBoolean() : true;
        if (!sessionService.get().canSendMessage() || !turnEnded) {
            // Bot is actively processing (streaming / awaiting RPC completion) —
            // queue the message for later delivery when the turn ends.
            String text = inputArea.getText();
            if (!text.isEmpty() && queueManager != null) {
                String clientMessageId = UUID.randomUUID().toString();
                queueManager.enqueue(text);
                chatPanel.addQueuedMessageId(clientMessageId);
                // Show local echo with queued indicator so the user sees their message.
                chatPanel.addMessage(new ProcessedMessage.Builder()
                    .messageType(MessageType.user_message_chunk)
                    .text(text)
                    .rawText(text)
                    .messageId(clientMessageId)
                    .queued(true)
                    .build());
                inputArea.setText("");
                messageHistory.add(text);
                // Attachments are intentionally dropped, not queued: when several
                // messages are queued they are later concatenated into ONE combined
                // prompt (see MessageQueueManager.flushAll), so per-message file
                // blocks would be ambiguous — there is no well-defined mapping of
                // "attach file X to queued message N" onto a single merged prompt.
                // The input is cleared so the user must re-attach after the turn ends.
                attachmentManager.clear();
                paperclipUpdater.run();
            }
            return;
        }
        String text = inputArea.getText(); // Don't trim user input spaces
        if (text.isEmpty() && attachmentManager.getAttachments().isEmpty()) {
            return;
        }

        // Block the send while a permission request awaits a decision: sending a new
        // message would supersede the pending request and hang the server, which is
        // still waiting on the tool-call decision. Keep the typed text in the input and
        // draw attention to the pending request instead of rejecting it.
        if (permissionPendingCheck != null && permissionPendingCheck.getAsBoolean()) {
            if (onPermissionBlockedCallback != null) {
                onPermissionBlockedCallback.run();
            }
            return;
        }

        if (onNewMessageCallback != null) {
            onNewMessageCallback.run();
        }

        // Add to history before slash intercept so local commands (/models, /title etc.)
        // are available via Alt+Up browsing.
        messageHistory.add(text);

        // Intercept local slash commands first (trim is only to check for '/')
        boolean isForwardedSlash = false;
        if (text.trim().startsWith("/")) {
            SlashCommandInterceptor interceptor = processService.get().getSlashCommandInterceptor();
            if (interceptor != null) {
                Lookup defaultCtx = Lookup.getDefault();
                CompletableFuture<Boolean> handled = interceptor.intercept(text, defaultCtx);
                if (handled != null && handled.isDone() && !handled.isCompletedExceptionally()) {
                    Boolean result = handled.join();
                    if (Boolean.TRUE.equals(result)) {
                        inputArea.setText("");
                        return;
                    }
                }
            }

            String echoText = ToolDataExtractor.getLocalEchoText(text);
            if (echoText != null) {
                chatPanel.addMessage(new ProcessedMessage.Builder()
                    .messageType(MessageType.tool_call_update)
                    .text(echoText)
                    .messageId(echoText)
                    .kind("Slash Command")
                    .toolTitle(echoText)
                    .rawText(echoText)
                    .build());
                inputArea.setText("");
                isForwardedSlash = true;
            }
        }

        String currentSessionId = sessionService.get().getCurrentSessionId();
        if (currentSessionId == null) {
            statusController.setStatus("STATUS_NoSession");
            return;
        }

        // Client-generated correlation ID so the local echo bubble can be matched
        // to the server-assigned message ID when it is echoed back.
        final String clientMessageId = UUID.randomUUID().toString();

        inputArea.setText("");
        statusController.setStatus("STATUS_Sending");
        statusController.startThinking();
        statusController.updateButtonState(true);

        // Build file attachment blocks (skip for forwarded slash commands)
        List<Map<String, Object>> fileBlocks = isForwardedSlash ? List.of() : attachmentManager.buildFileBlocks();
        if (!isForwardedSlash) {
            attachmentManager.clear();
            paperclipUpdater.run();

            // Local echo with file references
            if (localEchoEnabled) {
                StringBuilder echoBuilder = new StringBuilder(text);
                if (!fileBlocks.isEmpty()) {
                    if (!text.isBlank()) echoBuilder.append("\n");
                    for (Map<String, Object> block : fileBlocks) {
                        String type = (String) block.get("type");
                        String fname = (String) block.get("filename");
                        echoBuilder.append("\n[")
                                .append("image".equals(type)
                                        ? NbBundle.getMessage(MessageSender.class, "LBL_Image")
                                        : NbBundle.getMessage(MessageSender.class, "LBL_File")).append(": ")
                                .append(fname).append("]");
                    }
                }

                chatPanel.addMessage(new ProcessedMessage.Builder()
                    .messageType(MessageType.user_message_chunk)
                    .text(echoBuilder.toString())
                    .rawText(echoBuilder.toString())
                    .messageId(clientMessageId)
                    .build());
            }
        }

        final String messageText = text;
        if (onUserMessageSentCallback != null) {
            onUserMessageSentCallback.run();
        }
        if (PluginSettings.isAutoBackupChanges()) {
            final String sessionDir = sessionService.get().getCurrentSessionDirectory();
            RequestProcessor.getDefault().post(() -> {
                try {
                    VcsUtils.VcsInfo vcsInfo = VcsUtils.findVcsInfo(sessionDir);
                    if (vcsInfo != null && VcsUtils.hasUncommittedChanges(vcsInfo)) {
                        String backupMsg = VcsUtils.backupUncommittedChanges(vcsInfo);
                        if (backupMsg != null) {
                            SwingUtilities.invokeLater(() -> {
                                chatPanel.addMessage(new ProcessedMessage.Builder()
                                    .messageType(MessageType.tool_call_update)
                                    .text(backupMsg)
                                    .messageId(UUID.randomUUID().toString())
                                    .kind("System Backup")
                                    .toolTitle("Auto-Backup")
                                    .rawText(backupMsg)
                                    .build());
                            });
                        }
                    }
                } catch (Exception ex) {
                    LOG.info("Auto-backup failed: {0}", ExceptionUtils.getMessage(ex));
                }
            });
        }

        if (onBeforeServerSendCallback != null) {
            onBeforeServerSendCallback.run();
        }

        Map<String, Object> context = null;
        processService.get().sendMessage(currentSessionId, messageText, context, fileBlocks)
                .thenAccept(result -> {
                    // CPD-OFF — structural twin of sendQueuedMessage(); differences are
                    // per-method (logging, messageText vs combinedText, turn-end callback).
                    SwingUtilities.invokeLater(() -> {
                        LOG.info("RPC thenAccept fired (status during = {0}, hasStopReason = {1})",
                            statusController.getStatusText(),
                            result != null && result.has("stopReason"));
                        if (onMessageDoneCallback != null) {
                            onMessageDoneCallback.run();
                        }
                        // Always show Ready/Go. If messages were flushed,
                        // a delayed timer will switch back to Sending/Stop.
                        statusController.updateButtonState(false);
                        statusController.stopThinking();
                        statusController.setStatus("STATUS_Ready");
                        if (onTurnEndedCallback != null) {
                            onTurnEndedCallback.get();
                        }
                        inputFocusRequester.run();

                        // Handle turn completion from RPC result
                        if (result != null && result.has("stopReason")) {
                            LOG.info("Turn finished via RPC result: stopReason={0}", result.get("stopReason").asText());
                            // Reset the debounced flush timer (it's also reset on every
                            // processed message via drainMessageQueue), so the bubble is
                            // finalized 300ms after ALL messages drain — even if the RPC
                            // result arrives before the last SSE delta.
                            chatPanel.restartFlushTimer();
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        LOG.info("RPC exceptionally fired: {0}", ExceptionUtils.getMessage(ex));
                        statusController.setStatus("STATUS_Error",
                    ExceptionUtils.getMessage(ex) != null ? ExceptionUtils.getMessage(ex) : ex.getClass().getSimpleName());
                        statusController.stopThinking();
                        chatPanel.stopStreaming();
                        chatPanel.addMessage(ProcessedMessage.createError(
                                MessageType.error_response,
                                NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Error",
                                        ExceptionUtils.getMessage(ex) != null ? ExceptionUtils.getMessage(ex) : ex.getClass().getSimpleName()),
                                null, null
                        ));
                        inputArea.setText(messageText);
                        statusController.updateButtonState(false);
                        if (onMessageDoneCallback != null) {
                            onMessageDoneCallback.run();
                        }
                        if (onTurnEndedCallback != null) {
                            onTurnEndedCallback.get();
                        }
                        inputFocusRequester.run();
                    });
                    return null;
                });
        // CPD-ON
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
        String currentSessionId = sessionService.get().getCurrentSessionId();
        if (currentSessionId == null) {
            statusController.setStatus("STATUS_NoSession");
            return;
        }

        statusController.setStatus("STATUS_Sending");
        statusController.startThinking();
        statusController.updateButtonState(true);

        // No local echo — individual echoes were shown when queued.
        // No attachments — they were cleared when queued.
        // Editor Context — gated by system property to disable auto-injection
        // Reset turnEnded so SSE chunks for this combined turn correctly
        // switch the UI to the Stop (processing) state.
        if (onNewMessageCallback != null) {
            onNewMessageCallback.run();
        }

        if (onBeforeServerSendCallback != null) {
            onBeforeServerSendCallback.run();
        }

        Map<String, Object> context = null;
        processService.get().sendMessage(currentSessionId, combinedText, context, List.of())
                .thenAccept(result -> {
                    // CPD-OFF — structural twin of sendMessage(); differences are
                    // per-method (no logging, combinedText, no turn-end callback here).
                    SwingUtilities.invokeLater(() -> {
                        statusController.updateButtonState(false);
                        statusController.stopThinking();
                        if (onMessageDoneCallback != null) {
                            onMessageDoneCallback.run();
                        }
                        statusController.setStatus("STATUS_Ready");
                        inputFocusRequester.run();
                        if (result != null && result.has("stopReason")) {
                            chatPanel.restartFlushTimer();
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        statusController.setStatus("STATUS_Error",
                            ExceptionUtils.getMessage(ex) != null ? ExceptionUtils.getMessage(ex) : ex.getClass().getSimpleName());
                        statusController.stopThinking();
                        chatPanel.stopStreaming();
                        chatPanel.addMessage(ProcessedMessage.createError(
                                MessageType.error_response,
                                NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Error",
                                        ExceptionUtils.getMessage(ex) != null ? ExceptionUtils.getMessage(ex) : ex.getClass().getSimpleName()),
                                null, null
                        ));
                        inputArea.setText(combinedText);
                        statusController.updateButtonState(false);
                        if (onMessageDoneCallback != null) {
                            onMessageDoneCallback.run();
                        }
                        inputFocusRequester.run();
                    });
                    return null;
                });
        // CPD-ON
    }
}
