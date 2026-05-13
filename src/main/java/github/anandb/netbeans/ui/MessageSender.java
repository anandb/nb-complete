package github.anandb.netbeans.ui;

import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.manager.ProcessManager;
import github.anandb.netbeans.manager.SessionManager;
import github.anandb.netbeans.manager.SlashCommandInterceptor;
import github.anandb.netbeans.manager.ToolDataExtractor;
import github.anandb.netbeans.model.AttachedFile;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.support.Logger;
import org.openide.util.NbPreferences;
import org.openide.util.NbBundle;

/**
 * Handles sending user messages and stopping message processing.
 * Manages slash command interception, file attachments, local echo, editor context,
 * and the async RPC call for message delivery.
 */
public class MessageSender {

    private static final Logger LOG = new Logger(MessageSender.class);

    private final PlaceholderTextArea inputArea;
    private final ChatThreadPanel chatPanel;
    private final AttachmentManager attachmentManager;
    private final MessageHistory messageHistory;
    private final StatusController statusController;
    private final Runnable paperclipUpdater;
    private final Runnable inputFocusRequester;

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

    /** Sends (or intercepts) the current message text. */
    public void sendMessage() {
        if (!SessionManager.getInstance().getStateMachine().canSendMessage()) {
            return;
        }
        String text = inputArea.getText(); // Don't trim user input spaces
        if (text.isEmpty() && attachmentManager.getAttachments().isEmpty()) {
            return;
        }

        // Intercept local slash commands first
        boolean isForwardedSlash = text.trim().startsWith("/");
        if (isForwardedSlash) {
            SlashCommandInterceptor interceptor = ProcessManager.getInstance().getSlashCommandInterceptor();
            if (interceptor != null) {
                java.util.concurrent.CompletableFuture<Boolean> handled = interceptor.intercept(text, null);
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
            }
        }

        // Add to history
        if (!isForwardedSlash) {
            messageHistory.add(text);
        }

        String currentSessionId = SessionManager.getInstance().getCurrentSessionId();
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
                                .append("image".equals(type) ? "Image" : "File").append(": ")
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
        ProcessManager.getInstance().sendMessage(currentSessionId, messageText, context, fileBlocks)
                .thenAccept(result -> {
                    SwingUtilities.invokeLater(() -> {
                        String currentStatus = statusController.getStatusText();
                        if (currentStatus != null && currentStatus.equals(NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Sending"))) {
                            statusController.setStatus("STATUS_Ready");
                            statusController.stopThinking();
                            statusController.updateButtonState(false);
                            inputFocusRequester.run();
                        }

                        // Handle turn completion from RPC result
                        if (result != null && result.has("stopReason")) {
                            LOG.info("Turn finished via RPC result: stopReason={0}", result.get("stopReason").asText());
                            chatPanel.stopStreaming();
                        }
                    });
                })
                .exceptionally(ex -> {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        statusController.setStatus("STATUS_Error", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                        statusController.stopThinking();
                        chatPanel.stopStreaming();
                        chatPanel.addMessage(ProcessedMessage.createError(
                                MessageType.error_response,
                                NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Error", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()),
                                null, null
                        ));
                        inputArea.setText(messageText);
                        statusController.updateButtonState(false);
                        inputFocusRequester.run();
                    });
                    return null;
                });
    }

    /** Stops the currently processing message. */
    public void stopMessage() {
        if (!SessionManager.getInstance().getStateMachine().canStopMessage()) {
            return;
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            statusController.setStatus("STATUS_Stopping");
            statusController.startThinking();
        });
        SessionManager.getInstance().stopCurrentMessage();
        javax.swing.SwingUtilities.invokeLater(() -> {
            statusController.setStatus("STATUS_Stopped");
            statusController.stopThinking();
            chatPanel.stopStreaming();
            statusController.updateButtonState(false);
        });
    }
}
