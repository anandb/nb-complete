package github.anandb.netbeans.ui.session;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.model.HarnessCatalog;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import github.anandb.netbeans.ui.chat.ChatThreadPanel;
import github.anandb.netbeans.ui.composer.AttachmentManager;
import github.anandb.netbeans.ui.composer.MessageHistory;
import github.anandb.netbeans.ui.composer.PlaceholderTextArea;
import github.anandb.netbeans.ui.platform.SessionService;
import github.anandb.netbeans.ui.platform.ProcessService;

/**
 * The new-session config gate keeps the composer (Go + input) shut until Continue, so a
 * prompt sent before the preamble cannot land first and make the preamble a second
 * {@code session/prompt} during an active run. The guard in {@link MessageSender#sendMessage()}
 * is the last line of defence: even if the disabled button is bypassed, nothing may reach
 * the wire and the input must stay intact.
 */
class MessageSenderComposerLockTest {

    private static final String SESSION = "sess-1";

    private final StringBuilder input = new StringBuilder();
    private ChatThreadPanel chatPanel;
    private PlaceholderTextArea inputArea;
    private StatusController statusController;
    private AttachmentManager attachmentManager;
    private ProcessControl proc;
    private SlashCommandInterceptor interceptor;
    private MessageSender sender;

    @BeforeEach
    void setUp() throws Exception {
        chatPanel = mock(ChatThreadPanel.class);
        inputArea = mock(PlaceholderTextArea.class);
        statusController = mock(StatusController.class);
        when(statusController.isComposerLocked()).thenReturn(true);
        when(inputArea.getText()).thenAnswer(inv -> input.toString());
        org.mockito.Mockito.doAnswer(inv -> {
            input.setLength(0);
            input.append((String) inv.getArgument(0));
            return null;
        }).when(inputArea).setText(anyString());

        attachmentManager = new AttachmentManager();
        sender = new MessageSender(inputArea, chatPanel, attachmentManager, new MessageHistory(false),
                statusController, () -> { }, () -> { });

        SessionControl session = mock(SessionControl.class);
        when(session.canSendMessage()).thenReturn(true);
        when(session.getCurrentSessionId()).thenReturn(SESSION);

        proc = mock(ProcessControl.class);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        when(proc.supportsEmbeddedContext()).thenReturn(false);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(new CompletableFuture<JsonNode>());
        interceptor = mock(SlashCommandInterceptor.class);
        when(interceptor.intercept(anyString(), any())).thenReturn(CompletableFuture.completedFuture(false));
        when(proc.getSlashCommandInterceptor()).thenReturn(interceptor);

        Field sf = MessageSender.class.getDeclaredField("sessionService");
        sf.setAccessible(true);
        sf.set(sender, (SessionService) () -> session);
        Field pf = MessageSender.class.getDeclaredField("processService");
        pf.setAccessible(true);
        pf.set(sender, (ProcessService) () -> proc);
    }

    @Test
    void sendMessageDoesNothingWhileComposerIsLocked() {
        input.append("Explain the file I'm currently editing");

        sender.sendMessage();

        verify(chatPanel, never()).addMessage(any());
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
        assertEquals("Explain the file I'm currently editing", input.toString(),
                "the prompt must stay in the input while the gate is open");
    }
}