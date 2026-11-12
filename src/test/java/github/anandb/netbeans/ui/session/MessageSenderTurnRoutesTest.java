package github.anandb.netbeans.ui.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.RpcFailure;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.model.AttachedFile;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.support.InterjectSupport;
import github.anandb.netbeans.support.MapperSupplier;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import github.anandb.netbeans.ui.TestUiUtils;
import github.anandb.netbeans.ui.chat.ChatThreadPanel;
import github.anandb.netbeans.ui.composer.AttachmentManager;
import github.anandb.netbeans.ui.composer.MessageHistory;
import github.anandb.netbeans.ui.composer.PlaceholderTextArea;
import github.anandb.netbeans.ui.platform.SessionService;
import github.anandb.netbeans.ui.platform.ProcessService;

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
        InterjectSupport.reset();
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

    @AfterEach
    void tearDown() {
        InterjectSupport.reset();
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
    void compactCommandSendsWithoutClearingComposerDraft() throws Exception {
        session(true);
        input.append("keep this draft");

        sender.sendCompactCommand();

        verify(proc).sendMessage(SESSION, "/compact", null, java.util.List.of());
        assertEquals("keep this draft", input.toString());
        verify(inputArea, never()).setText("");
    }

    @Test
    void compactCommandQueuesMidTurnAndPreservesDraftAndAttachments() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.GOOSE);
        when(proc.getActiveRunId(SESSION)).thenReturn(null);
        input.append("keep this draft");
        attachments.add(new AttachedFile("draft.txt", "text/plain", new byte[] {1}, 1,
                "file:///draft.txt"));

        sender.sendCompactCommand();

        verify(queue).enqueue("/compact");
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
        assertEquals("keep this draft", input.toString());
        assertEquals(1, attachments.size());
    }

    @Test
    void compactCommandFailureDoesNotPutCommandInComposer() throws Exception {
        session(true);
        CompletableFuture<JsonNode> pending = new CompletableFuture<>();
        when(proc.sendMessage(anyString(), anyString(), any(), any())).thenReturn(pending);

        sender.sendCompactCommand();
        pending.completeExceptionally(new RuntimeException("compact failed"));
        drainEdt();

        assertEquals("", input.toString());
    }

    @Test
    void compactCommandRespectsComposerLock() throws Exception {
        session(true);
        input.append("keep this draft");
        when(statusController.isComposerLocked()).thenReturn(true);

        sender.sendCompactCommand();

        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
        assertEquals("keep this draft", input.toString());
    }

    @Test
    void compactCommandWithNoSessionDoesNotSendOrChangeDraft() throws Exception {
        SessionControl session = mock(SessionControl.class);
        when(session.getCurrentSessionId()).thenReturn(null);
        inject("sessionService", (SessionService) () -> session);
        input.append("keep this draft");

        sender.sendCompactCommand();

        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
        assertEquals("keep this draft", input.toString());
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
    void anAgentThatAdvertisesSteeringInjectsInsteadOfQueueing() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.PI);
        when(proc.supportsSessionSteering()).thenReturn(true);
        when(proc.steerSession(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        input.append("hello");

        sender.sendMessage();

        verify(proc).steerSession(anyString(), anyString(), any());
        // PI requiresMessageQueue: steering must win over the queue.
        verify(queue, never()).enqueue(anyString());
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void aFailedProtocolSteerRestoresTheTextAndKeepsTheTurnAlive() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.PI);
        when(proc.supportsSessionSteering()).thenReturn(true);
        when(proc.steerSession(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        assertEquals("hello", input.toString(), "the text goes back so it can be resent");
        verify(chatPanel, never()).stopStreaming();
        verify(statusController).updateButtonState(true);
        verify(statusController, never()).updateButtonState(false);
        verify(statusController, never()).disarmRunWatchdog();
        assertEquals(false, messageDone.get(), "the running turn is not done because of the steer");
        assertEquals(false, turnEndedCallbackRan.get());
    }

    @Test
    void aFailedGrokInterjectRestoresTheTextAndKeepsTheTurnAlive() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.GROK);
        when(proc.supportsSessionSteering()).thenReturn(false);
        InterjectSupport.set(true);
        when(proc.interjectMessage(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        assertEquals("hello", input.toString(), "the text goes back so it can be resent");
        verify(proc).interjectMessage(anyString(), anyString(), any());
        verify(statusController).updateButtonState(true);
        verify(statusController, never()).updateButtonState(false);
        verify(statusController, never()).disarmRunWatchdog();
        assertEquals(false, messageDone.get(), "the running turn is not done because of the steer");
        assertEquals(false, turnEndedCallbackRan.get());
    }

    @Test
    void aSteerRejectedBecauseTheTurnEndedRestoresTheText() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.PI);
        when(proc.supportsSessionSteering()).thenReturn(true);
        ObjectNode reply = MapperSupplier.get().createObjectNode().put("outcome", "promptRequired");
        when(proc.steerSession(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(reply));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        assertEquals("hello", input.toString(), "a steer that missed its turn must not swallow the text");
        verify(queue, never()).enqueue(anyString());
        verify(chatPanel).removeMessageBubble(anyString());
        verify(chatPanel, never()).stopStreaming();
        assertEquals(false, messageDone.get());
    }

    @Test
    void anAgentWithoutTheExtensionKeepsTheQueueRoute() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.PI);
        when(proc.supportsSessionSteering()).thenReturn(false);
        input.append("hello");

        sender.sendMessage();

        verify(queue).enqueue("hello");
        verify(proc, never()).steerSession(anyString(), anyString(), any());
    }

    @Test
    void aHermesHarnessSteersThroughTheSlashCommand() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.HERMES);
        input.append("hello");

        sender.sendMessage();

        verify(proc).sendMessage(SESSION, "/steer hello", null, java.util.List.of());
        verify(proc, never()).steerMessage(anyString(), anyString(), any());
        verify(queue, never()).enqueue(anyString());
    }

    @Test
    void anExplicitSteerCommandIsNotPrefixedTwice() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.HERMES);
        input.append("/steer keep it focused");

        sender.sendMessage();

        verify(proc).sendMessage(SESSION, "/steer keep it focused", null, java.util.List.of());
    }

    @Test
    void aSlashSteerWithAttachmentsFallsBackToQueueing() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.HERMES);
        input.append("hello");
        attachments.add(attachment("a.txt"));

        sender.sendMessage();

        verify(queue).enqueue("hello");
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void anotherSlashCommandIsNotConvertedIntoASteer() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.HERMES);
        input.append("/queue next");

        sender.sendMessage();

        verify(queue).enqueue("/queue next");
        verify(proc, never()).sendMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void aFailedSlashSteerRestoresTheTextAndKeepsTheTurnAlive() throws Exception {
        session(false);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.HERMES);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        assertEquals("hello", input.toString(), "the text goes back so it can be resent");
        verify(chatPanel, never()).stopStreaming();
        verify(statusController).updateButtonState(true);
        verify(statusController, never()).updateButtonState(false);
        verify(statusController, never()).disarmRunWatchdog();
        assertEquals(false, messageDone.get(), "the running turn is not done because of the steer");
        assertEquals(false, turnEndedCallbackRan.get());
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

    @Test
    void aTruncatedTurnShowsAVisibleNotice() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        CompletableFuture<JsonNode> pending = new CompletableFuture<>();
        when(proc.sendMessage(anyString(), anyString(), any(), any())).thenReturn(pending);
        input.append("hello");

        sender.sendMessage();
        pending.complete(MapperSupplier.get().createObjectNode().put("stopReason", "max_tokens"));
        drainEdt();

        assertTrue(drawnNotices() > 0, "a reply cut short by the output limit is not just logged");
    }

    @Test
    void aNormalTurnShowsNoTruncationNotice() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        CompletableFuture<JsonNode> pending = new CompletableFuture<>();
        when(proc.sendMessage(anyString(), anyString(), any(), any())).thenReturn(pending);
        input.append("hello");

        sender.sendMessage();
        pending.complete(MapperSupplier.get().createObjectNode().put("stopReason", "end_turn"));
        drainEdt();

        assertEquals(0, drawnNotices());
    }

    @Test
    void anAuthenticationFailureTellsTheUserToSignIn() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new FakeRpcFailure(
                        RpcFailure.AUTH_REQUIRED, "Authentication required",
                        Map.of("reason", RpcFailure.REASON_TURN_FAILED))));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        assertTrue(lastErrorText().contains("Sign in"),
                "an auth failure must say what to do, not just repeat the server text");
    }

    @Test
    void anExhaustedBalanceShowsTheTopUpUrl() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new FakeRpcFailure(
                        RpcFailure.INTERNAL_ERROR, "Credits exhausted",
                        Map.of("reason", RpcFailure.REASON_CREDITS_EXHAUSTED,
                               "url", "https://example.test/top-up"))));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        assertTrue(lastErrorText().contains("https://example.test/top-up"),
                "the top-up link goose sent must reach the user");
    }

    @Test
    void aRateLimitedPromptShowsTheProviderQuotaDetail() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new FakeRpcFailure(
                        RpcFailure.RATE_LIMITED, "Rate limited",
                        Map.of("message", "You've used all the included free usage for grok-4.7"))));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        String text = lastErrorText();
        assertTrue(text.contains("Rate limited"), "the failure must be named: " + text);
        assertTrue(text.contains("free usage"),
                "grok's quota detail lives in data.message and must reach the user: " + text);
    }

    @Test
    void aRateLimitedPromptWithoutDetailFallsBackToTheServerText() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new FakeRpcFailure(
                        RpcFailure.RATE_LIMITED, "Rate limited", Map.of())));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        assertTrue(lastErrorText().contains("Rate limited"), "no detail: keep the server text");
    }

    @Test
    void anUnclassifiedFailureKeepsThePlainServerMessage() throws Exception {
        session(true);
        when(proc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        when(proc.sendMessage(anyString(), anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        input.append("hello");

        sender.sendMessage();
        drainEdt();

        assertTrue(lastErrorText().contains("boom"));
        assertTrue(!lastErrorText().contains("Sign in"), "no auth hint on an unrelated failure");
    }

    /** The text of the last error bubble drawn. */
    private String lastErrorText() {
        ArgumentCaptor<ProcessedMessage> drawn = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(chatPanel, atLeastOnce()).addMessage(drawn.capture());
        return drawn.getAllValues().stream()
                .filter(pm -> pm.messageType() == MessageType.error_response)
                .map(ProcessedMessage::text)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    /** An RPC failure as the manager hands it to the UI: a throwable that knows its code and data. */
    private static final class FakeRpcFailure extends RuntimeException implements RpcFailure {
        private static final long serialVersionUID = 1L;
        private final int code;
        private final Map<String, String> data;

        FakeRpcFailure(int code, String message, Map<String, String> data) {
            super(message);
            this.code = code;
            this.data = data;
        }

        @Override
        public int code() {
            return code;
        }

        @Override
        public String dataField(String field) {
            return data.get(field);
        }
    }

    /** Info bubbles drawn so far that are the truncation notice. */
    private long drawnNotices() {
        ArgumentCaptor<ProcessedMessage> drawn = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(chatPanel, atLeastOnce()).addMessage(drawn.capture());
        return drawn.getAllValues().stream()
                .filter(pm -> pm.messageType() == MessageType.info)
                .filter(pm -> pm.text() != null && pm.text().contains("output-token"))
                .count();
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
