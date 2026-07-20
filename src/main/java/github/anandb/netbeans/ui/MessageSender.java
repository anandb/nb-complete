package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    /** Sends (or intercepts) the current message text. */
    public void sendMessage() {
        if (!sessionService.get().canSendMessage()) {
            return;
        }
        String text = inputArea.getText(); // Don't trim user input spaces
        if (text.isEmpty() && attachmentManager.getAttachments().isEmpty()) {
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

        // Editor Context
        Map<String, Object> context = isForwardedSlash ? null : EditorContextCapture.capture();

        final String messageText = text;
        if (onUserMessageSentCallback != null) {
            onUserMessageSentCallback.run();
        }
        processService.get().sendMessage(currentSessionId, messageText, context, fileBlocks)
                .thenAccept(result -> {
                    SwingUtilities.invokeLater(() -> {
                        LOG.info("RPC thenAccept fired (status during = {0}, hasStopReason = {1})",
                            statusController.getStatusText(),
                            result != null && result.has("stopReason"));
                        // Always reset button/flag on RPC completion — the status text guard
                        // is unreliable because SSE may have already changed the status away
                        // from "Sending". Setting turnEnded=true prevents displayMessage()
                        // from calling updateButtonState(true) on any late SSE messages.
                        statusController.updateButtonState(false);
                        statusController.stopThinking();
                        if (onMessageDoneCallback != null) {
                            onMessageDoneCallback.run();
                        }
                        statusController.setStatus("STATUS_Ready");
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
                        inputFocusRequester.run();
                    });
                    return null;
                });
    }

    /** Stops the currently processing message. */
    public void stopMessage() {
        if (!sessionService.get().canStopMessage()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            statusController.setStatus("STATUS_Stopping");
            statusController.startThinking();
        });
        // stopCurrentMessage may block on pipe I/O (writer.println).
        // Run off EDT to avoid freezing the UI.
        CompletableFuture.runAsync(() ->
            sessionService.get().stopCurrentMessage()
        );
        // Show "Stopped" immediately — don't wait for cancel notification to be sent.
        SwingUtilities.invokeLater(() -> {
            statusController.setStatus("STATUS_Stopped");
            statusController.stopThinking();
            chatPanel.stopStreaming();
            statusController.updateButtonState(false);
        });
    }
}
