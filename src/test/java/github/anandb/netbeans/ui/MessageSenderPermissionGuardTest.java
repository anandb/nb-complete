package github.anandb.netbeans.ui;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.ui.platform.ProcessService;
import github.anandb.netbeans.ui.platform.SessionService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The permission guard sits below the slash interception, so local commands keep working
 * while a permission prompt is open. A forwarded command is a real send and must be
 * blocked before it echoes or clears the input.
 */
class MessageSenderPermissionGuardTest {

    private static final String SESSION = "sess-1";

    private final StringBuilder input = new StringBuilder();
    private final AtomicBoolean blocked = new AtomicBoolean();
    private ChatThreadPanel chatPanel;
    private PlaceholderTextArea inputArea;
    private StatusController statusController;
    private ProcessControl proc;
    private SlashCommandInterceptor interceptor;
    private MessageSender sender;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        chatPanel = mock(ChatThreadPanel.class);
        inputArea = mock(PlaceholderTextArea.class);
        statusController = mock(StatusController.class);
        when(inputArea.getText()).thenAnswer(inv -> input.toString());
        doAnswer(inv -> {
            input.setLength(0);
            input.append((String) inv.getArgument(0));
            return null;
        }).when(inputArea).setText(anyString());

        AttachmentManager attachments = mock(AttachmentManager.class);
        when(attachments.getAttachments()).thenReturn(List.of());
        when(attachments.buildFileBlocks(anyBoolean())).thenReturn(List.of());

        sender = new MessageSender(inputArea, chatPanel, attachments, new MessageHistory(false),
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

        inject("sessionService", (SessionService) () -> session);
        inject("processService", (ProcessService) () -> proc);
        sender.setOnPermissionBlockedCallback(() -> blocked.set(true));
    }

    private void inject(String field, Object value) throws Exception {
        Field f = MessageSender.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(sender, value);
    }

    private void permissionPending(boolean pending) {
        sender.setPermissionPendingCheck(() -> pending);
    }

    @Test
    void localSlashCommandStillRunsWhilePermissionIsPending() {
        permissionPending(true);
        when(interceptor.intercept(anyString(), any())).thenReturn(CompletableFuture.completedFuture(true));
        input.append("/models");

        sender.sendMessage();

        verify(interceptor).intercept(anyString(), any());
        verify(chatPanel, never()).addMessage(any());
        assertEquals("", input.toString(), "a handled command clears the input");
        assertFalse(blocked.get(), "local commands must not reach the permission guard");
    }

    @Test
    void forwardedSlashCommandIsBlockedBeforeItEchoesOrClears() {
        permissionPending(true);
        input.append("/compact");

        sender.sendMessage();

        verify(chatPanel, never()).addMessage(any());
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
        assertEquals("/compact", input.toString(), "the text must stay in the input");
        assertTrue(blocked.get(), "the pending request must be surfaced");
    }

    @Test
    void forwardedSlashCommandEchoesOnceTheSendIsAccepted() {
        permissionPending(false);
        input.append("/compact");

        sender.sendMessage();

        ArgumentCaptor<ProcessedMessage> captor = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(chatPanel).addMessage(captor.capture());
        ProcessedMessage echo = captor.getValue();
        assertEquals("Slash Command", echo.kind());
        assertEquals("compact", echo.text());
        assertEquals("", input.toString());
        assertFalse(blocked.get());
        verify(proc).sendMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void plainMessageIsBlockedAndKeptInTheInput() {
        permissionPending(true);
        input.append("hello");

        sender.sendMessage();

        verify(chatPanel, never()).addMessage(any());
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
        assertEquals("hello", input.toString());
        assertTrue(blocked.get());
    }

    @Test
    void permissionArrivingDuringInterceptionIsStillSeenByTheGuard() {
        permissionPending(false);
        when(interceptor.intercept(anyString(), any())).thenAnswer(inv -> {
            permissionPending(true); // request arrives while the command popup is up
            return CompletableFuture.completedFuture(false);
        });
        input.append("/nothing-handled");

        sender.sendMessage();

        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
        assertEquals("/nothing-handled", input.toString());
        assertTrue(blocked.get());
    }

    @Test
    void nothingIsProcessedWithoutASession() throws Exception {
        permissionPending(false);
        SessionControl noSession = mock(SessionControl.class);
        when(noSession.canSendMessage()).thenReturn(true);
        when(noSession.getCurrentSessionId()).thenReturn(null);
        inject("sessionService", (SessionService) () -> noSession);
        when(interceptor.intercept(anyString(), any())).thenReturn(CompletableFuture.completedFuture(true));
        input.append("/models");

        sender.sendMessage();

        verify(interceptor, never()).intercept(anyString(), any());
        verify(chatPanel, never()).addMessage(any());
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
        verify(statusController).setStatus("STATUS_NoSession");
        assertEquals("/models", input.toString(), "the text stays in the input");
        assertFalse(blocked.get());
    }

    @Test
    void midTurnMessageIsQueuedForAQueueingHarness() throws Exception {
        permissionPending(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.GOOSE);
        SessionControl midTurn = mock(SessionControl.class);
        when(midTurn.canSendMessage()).thenReturn(false);
        when(midTurn.getCurrentSessionId()).thenReturn(SESSION);
        inject("sessionService", (SessionService) () -> midTurn);
        MessageQueueManager queue = mock(MessageQueueManager.class);
        sender.setQueueManager(queue);
        input.append("hello");

        sender.sendMessage();

        verify(queue).enqueue("hello");
        verify(chatPanel, never()).addMessage(any());
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
        assertEquals("", input.toString(), "the text moved to the queue");
    }
}
