package github.anandb.netbeans.ui;

import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import java.util.concurrent.CompletableFuture;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.support.ToolDataExtractor;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.TimingConstants;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.NbBundle;

/**
 * Handles sending user messages and stopping message processing.
 * Manages slash command interception, file attachments, local echo, editor context,
 * and the async RPC call for message delivery.
 */
public class MessageSender {

    private static final Logger LOG = Logger.from(MessageSender.class);

    private final PlaceholderTextArea inputArea;
    private final ChatThreadPanel chatPanel;
    private final AttachmentManager attachmentManager;
    private final MessageHistory messageHistory;
    private final StatusController statusController;
    private final Runnable paperclipUpdater;
    private final Runnable inputFocusRequester;
    private Runnable onNewMessageCallback;
    private Runnable onMessageDoneCallback;

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
    }

    public void setOnNewMessageCallback(Runnable callback) {
        this.onNewMessageCallback = callback;
    }

    public void setOnMessageDoneCallback(Runnable callback) {
        this.onMessageDoneCallback = callback;
    }

    /** Sends (or intercepts) the current message text. */
    public void sendMessage() {
        if (!Lookup.getDefault().lookup(SessionControl.class).canSendMessage()) {
            return;
        }
        String text = inputArea.getText(); // Don't trim user input spaces
        if (text.isEmpty() && attachmentManager.getAttachments().isEmpty()) {
            return;
        }

        if (onNewMessageCallback != null) {
            onNewMessageCallback.run();
        }

        // Intercept local slash commands first (trim is only to check for '/')
        boolean isForwardedSlash = false;
        if (text.trim().startsWith("/")) {
            SlashCommandInterceptor interceptor = Lookup.getDefault().lookup(ProcessControl.class).getSlashCommandInterceptor();
            if (interceptor != null) {
                CompletableFuture<Boolean> handled = interceptor.intercept(text, null);
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

        // Add to history
        if (!isForwardedSlash) {
            messageHistory.add(text);
        }

        String currentSessionId = Lookup.getDefault().lookup(SessionControl.class).getCurrentSessionId();
        if (currentSessionId == null) {
            statusController.setStatus("STATUS_NoSession");
            return;
        }

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
            boolean localEcho = NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("echoUserInput", true);
            if (localEcho) {
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
                    .build());
            }
        }

        // Editor Context
        Map<String, Object> context = isForwardedSlash ? null : EditorContextCapture.capture();

        final String messageText = text;
        Lookup.getDefault().lookup(ProcessControl.class).sendMessage(currentSessionId, messageText, context, fileBlocks)
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
                            // Brief delay to allow any in-flight delta notifications to arrive
                            // before finalizing the stream bubble (opencode may send result before last delta)
                            Timer flushTimer = new Timer(TimingConstants.STREAM_FLUSH_MS, e -> {
                                if (chatPanel.isDisplayable()) {
                                    chatPanel.stopStreaming();
                                }
                            });
                            flushTimer.setRepeats(false);
                            flushTimer.start();
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        LOG.info("RPC exceptionally fired: {0}", ex.getMessage());
                        statusController.setStatus("STATUS_Error", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                        statusController.stopThinking();
                        chatPanel.stopStreaming();
                        chatPanel.addMessage(ProcessedMessage.createError(
                                MessageType.error_response,
                                NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Error",
                                        ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()),
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
        if (!Lookup.getDefault().lookup(SessionControl.class).canStopMessage()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            statusController.setStatus("STATUS_Stopping");
            statusController.startThinking();
        });
        // stopCurrentMessage may block on pipe I/O (writer.println).
        // Run off EDT to avoid freezing the UI.
        CompletableFuture.runAsync(() ->
            Lookup.getDefault().lookup(SessionControl.class).stopCurrentMessage()
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
