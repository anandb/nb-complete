package github.anandb.netbeans.ui;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.model.AttachedFile;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.ui.platform.ProcessService;
import github.anandb.netbeans.ui.platform.SessionService;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The three mid-turn routes and what a failed send does to the turn.
 *
 * <p>Order: a harness with its own steering extension steers, a queueing harness queues,
 * and everything else accepts a second {@code session/prompt} — asserted as an ordering,
 * not one route at a time. Failure: a failed <em>turn-starting</em> send tears the turn
 * down, while a failed mid-turn send must leave Stop, the watchdog and the streaming
 * bubble alone because the harness's first turn keeps running.</p>
 */
class MessageSenderTurnRoutesTest {

    private static final String SESSION = "sess-1";

    @TempDir
    Path tempDir;

    private final StringBuilder input = new StringBuilder();
    private final AtomicBoolean messageDone = new AtomicBoolean();
    private final AtomicBoolean turnEndedCallbackRan = new AtomicBoolean();
    private ChatThreadPanel chatPanel;
    private PlaceholderTextArea inputArea;
    private StatusController statusController;
    private AttachmentManager attachments;
    private ProcessControl proc;
    private MessageQueueManager queue;
    private MessageSender sender;

    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        chatPanel = mock(ChatThreadPanel.class);
        inputArea = mock(PlaceholderTextArea.class);
        statusController = mock(StatusController.class);
        attachments = new AttachmentManager();
        when(inputArea.getText()).thenAnswer(inv -> input.toString());
        doAnswer(inv -> {
            input.setLength(0);
            input.append((String) inv.getArgument(0));
            return null;
        }).when(inputArea).setText(anyString());

        sender = new MessageSender(inputArea, chatPanel, attachments, new MessageHistory(false),
                statusController, () -> { }, () -> { });
        sender.setOnMessageDoneCallback(() -> messageDone.set(true));
        sender.setOnTurnEndedCallback(() -> {
            turnEndedCallbackRan.set(true);
            return true;
        });
        queue = mock(MessageQueueManager.class);
        sender.setQueueManager(queue);

        proc = mock(ProcessControl.class);
        when(proc.sendMessage(anyString(), anyString(), any(), any())).thenReturn(new CompletableFuture<>());
        SlashCommandInterceptor interceptor = mock(SlashCommandInterceptor.class);
        when(interceptor.intercept(anyString(), any())).thenReturn(CompletableFuture.completedFuture(false));
        when(proc.getSlashCommandInterceptor()).thenReturn(interceptor);
        inject("processService", (ProcessService) () -> proc);
    }

    private void inject(String field, Object value) throws Exception {
        Field f = MessageSender.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(sender, value);
    }

    /** A session whose turn is in flight ({@code idle == false}) or finished. */
    private void session(boolean idle) throws Exception {
        SessionControl session = mock(SessionControl.class);
        when(session.canSendMessage()).thenReturn(idle);
        when(session.getCurrentSessionId()).thenReturn(SESSION);
        inject("sessionService", (SessionService) () -> session);
        sender.setTurnEndedCheck(() -> idle);
    }

    private void drainEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void aSteerableHarnessSteersInsteadOfQueueingOrSending() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.GOOSE);
        when(proc.getActiveRunId(SESSION)).thenReturn("run-1");
        when(proc.steerMessage(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        input.append("hello");

        sender.sendMessage();

        verify(proc).steerMessage(anyString(), anyString(), any());
        verify(queue, never()).enqueue(anyString());
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void aQueueingHarnessWithoutARunIdQueuesInsteadOfSteering() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.GOOSE);
        when(proc.getActiveRunId(SESSION)).thenReturn(null);
        input.append("hello");

        sender.sendMessage();

        verify(queue).enqueue("hello");
        verify(proc, never()).steerMessage(anyString(), anyString(), any());
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void anInterleavingHarnessTakesASecondPromptMidTurn() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        input.append("hello");

        sender.sendMessage();

        // OpenCode v1: no steering extension and no queue, so the prompt is posted now.
        verify(proc).sendMessage(anyString(), anyString(), any(), any());
        verify(proc, never()).steerMessage(anyString(), anyString(), any());
        verify(queue, never()).enqueue(anyString());
    }

    @Test
    void aFailedTurnStartingSendEndsTheTurn() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        verify(chatPanel).stopStreaming();
        verify(statusController).disarmRunWatchdog();
        verify(statusController).updateButtonState(false);
        assertEquals("hello", input.toString(), "the text goes back so it can be resent");
        assertEquals(true, messageDone.get(), "the turn is over, so the message is done");
        assertEquals(true, turnEndedCallbackRan.get());
    }

    @Test
    void aFailedSecondPromptMidTurnKeepsTheTurnAlive() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        // The harness's first turn is still running: nothing may end its UI.
        verify(chatPanel, never()).stopStreaming();
        verify(statusController, never()).disarmRunWatchdog();
        // beginSending already put the panel in Sending/Stop; the failure must not undo it.
        verify(statusController, never()).updateButtonState(false);
        assertEquals("hello", input.toString());
        assertEquals(false, messageDone.get(), "the running turn is not done because of this send");
        assertEquals(false, turnEndedCallbackRan.get());
    }

    @Test
    void aFailedTurnStartingSendPutsTheAttachmentsBack() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        input.append("hello");
        attachments.add(attachment("a.txt"));

        sender.sendMessage();
        drainEdt();

        assertEquals("a.txt", onlyAttachment(), "the file goes back with the text");
        assertEquals("hello", input.toString());
    }

    @Test
    void aFailedSecondPromptMidTurnPutsTheAttachmentsBack() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        input.append("hello");
        attachments.add(attachment("a.txt"));

        sender.sendMessage();
        drainEdt();

        assertEquals("a.txt", onlyAttachment());
    }

    @Test
    void aFailedSteerPutsTheAttachmentsBack() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.GOOSE);
        when(proc.getActiveRunId(SESSION)).thenReturn("run-1");
        CompletableFuture<JsonNode> steer = new CompletableFuture<>();
        when(proc.steerMessage(anyString(), anyString(), any())).thenReturn(steer);
        input.append("hello");
        attachments.add(attachment("a.txt"));

        sender.sendMessage();
        steer.completeExceptionally(new RuntimeException("boom"));
        drainEdt();

        assertEquals("a.txt", onlyAttachment());
        assertEquals("hello", input.toString());
    }

    @Test
    void attachmentsTheCapsCannotTakeBackAreReported() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        CompletableFuture<JsonNode> pending = new CompletableFuture<>();
        when(proc.sendMessage(anyString(), anyString(), any(), any())).thenReturn(pending);
        input.append("hello");
        attachments.add(attachment("sent.txt"));

        sender.sendMessage();
        // The user attaches two more files while the prompt is in flight, filling the composer.
        attachments.add(attachment("b.txt"));
        attachments.add(attachment("c.txt"));
        pending.completeExceptionally(new RuntimeException("boom"));
        drainEdt();

        assertEquals(2, attachments.size(), "the two files attached during the flight stay");
        verify(statusController).setStatus("STATUS_AttachmentsDropped", 1);
    }

    @Test
    void aFailedTurnStartingSendKeepsTextTypedDuringTheFlight() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        CompletableFuture<JsonNode> pending = new CompletableFuture<>();
        when(proc.sendMessage(anyString(), anyString(), any(), any())).thenReturn(pending);
        input.append("hello");

        sender.sendMessage();
        input.append("next idea");
        pending.completeExceptionally(new RuntimeException("boom"));
        drainEdt();

        assertEquals("next idea", input.toString(),
                "the newer text stays; the failed one is still in the Alt+Up history");
    }

    @Test
    void aFailedQueuedSendDropsItsEchoAndKeepsTextTypedDuringTheFlight() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.GOOSE);
        CompletableFuture<JsonNode> pending = new CompletableFuture<>();
        when(proc.sendMessage(anyString(), anyString(), any(), any())).thenReturn(pending);

        sender.sendQueuedMessage("queued");
        input.append("typed while waiting");
        pending.completeExceptionally(new RuntimeException("boom"));
        drainEdt();

        ArgumentCaptor<ProcessedMessage> drawn = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(chatPanel, atLeastOnce()).addMessage(drawn.capture());
        String echoId = drawn.getAllValues().stream()
                .filter(pm -> pm.messageType().isUser())
                .map(ProcessedMessage::messageId)
                .findFirst().orElseThrow();
        verify(chatPanel).removeMessageBubble(echoId);
        assertEquals("typed while waiting", input.toString(), "text typed in the meantime is newer");
    }

    /** The one attachment left in the composer, by name. */
    private String onlyAttachment() {
        assertEquals(1, attachments.size(), "exactly one file is back in the composer");
        return attachments.getAttachments().get(0).filename();
    }

    private AttachedFile attachment(String name) throws IOException {
        Path file = Files.writeString(tempDir.resolve(name), "content of " + name);
        return new AttachedFile(file.toFile());
    }
}
