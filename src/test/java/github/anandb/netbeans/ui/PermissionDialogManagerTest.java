package github.anandb.netbeans.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.ui.platform.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionDialogManagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNode EMPTY_PARAMS = MAPPER.createObjectNode();
    private static final String TEST_SESSION = "test-session";

    /** Test double that records showRequest calls instead of rendering UI. */
    private static final class PermissionPanelSpy {
        final List<ShowRequestCall> calls = new ArrayList<>();

        void showRequest(String prompt, JsonNode options,
                CompletableFuture<String> response, JsonNode toolCall) {
            calls.add(new ShowRequestCall(prompt, options, response, toolCall));
        }

        int showRequestCount() { return calls.size(); }

        record ShowRequestCall(String prompt, JsonNode options,
                CompletableFuture<String> response, JsonNode toolCall) {}
    }

    /** Minimal ChatThreadPanel test double. */
    private static final class ChatPanelSpy {
        final List<String> results = new ArrayList<>();
        final List<Boolean> allowedList = new ArrayList<>();

        void addPermissionResult(String statusText, boolean allowed) {
            results.add(statusText);
            allowedList.add(allowed);
        }
    }

    private ChatPanelSpy chatPanel;
    private PermissionPanelSpy permissionPanel;
    private PermissionDialogManager manager;
    private Queue<Runnable> requestQueue;
    private Method processNextRequestMethod;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        TestUiUtils.setupTestUIManager();
        chatPanel = new ChatPanelSpy();
        permissionPanel = new PermissionPanelSpy();

        // Create manager with mocked SessionControl
        SessionControl sessionControl = mock(SessionControl.class);
        when(sessionControl.getCurrentSessionId()).thenReturn(TEST_SESSION);
        when(sessionControl.isDescendantOfCurrent(anyString())).thenReturn(false);
        when(sessionControl.getCustomTitle(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));

        SessionService sessionService = () -> sessionControl;

        // Use reflection to create manager without calling the constructor
        // that initializes sessionService from PlatformBridge
        ChatThreadPanel chatPanelMock = mock(ChatThreadPanel.class);
        doAnswer(inv -> {
            chatPanel.addPermissionResult(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(chatPanelMock).addPermissionResult(anyString(), anyBoolean());

        manager = new PermissionDialogManager(chatPanelMock, createPanelMock());
        // Override the sessionService field
        Field svcField = PermissionDialogManager.class.getDeclaredField("sessionService");
        svcField.setAccessible(true);
        svcField.set(manager, sessionService);

        Field queueField = PermissionDialogManager.class.getDeclaredField("requestQueue");
        queueField.setAccessible(true);
        requestQueue = (Queue<Runnable>) queueField.get(manager);

        processNextRequestMethod = PermissionDialogManager.class
                .getDeclaredMethod("processNextRequest");
        processNextRequestMethod.setAccessible(true);
    }

    /** Creates a PermissionRequestPanel mock that delegates to our spy. */
    @SuppressWarnings("unchecked")
    private PermissionRequestPanel createPanelMock() {
        PermissionRequestPanel panel = mock(PermissionRequestPanel.class);
        doAnswer(inv -> {
            permissionPanel.showRequest(
                    inv.getArgument(0), inv.getArgument(1),
                    inv.getArgument(2), inv.getArgument(3));
            return null;
        }).when(panel).showRequest(anyString(), any(), any(), any());
        return panel;
    }

    private boolean isRequestShowing() throws Exception {
        Field f = PermissionDialogManager.class.getDeclaredField("isRequestShowing");
        f.setAccessible(true);
        return f.getBoolean(manager);
    }

    private static void onEDT(Runnable task) throws Exception {
        SwingUtilities.invokeAndWait(task);
    }

    private void processNextOnEDT() throws Exception {
        onEDT(() -> {
            try {
                processNextRequestMethod.invoke(manager);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Runnable buildShowTask(String prompt, CompletableFuture<String> response,
            Runnable activateCallback) {
        return () -> {
            try {
                permissionPanel.showRequest(prompt, EMPTY_PARAMS, response, null);
                activateCallback.run();
            } catch (Exception e) {
                if (!response.isDone()) {
                    response.complete("reject");
                }
                try {
                    processNextRequestMethod.invoke(manager);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
    }

    // ===================================================================
    // handlePermissionRequest session filtering (synchronous, no EDT)
    // ===================================================================

    @Test
    void testAcceptsCurrentSession() throws Exception {
        CompletableFuture<String> response = new CompletableFuture<>();
        manager.handlePermissionRequest(TEST_SESSION, EMPTY_PARAMS, response, () -> {});
        assertFalse(response.isDone(), "Request for current session should not be rejected");
        onEDT(() -> {});
        assertEquals(1, permissionPanel.showRequestCount());
    }

    @Test
    void testRejectsUnrelatedSession() {
        CompletableFuture<String> response = new CompletableFuture<>();
        manager.handlePermissionRequest("other-session", EMPTY_PARAMS, response, () -> {});
        assertTrue(response.isDone());
        assertEquals("reject", response.join());
    }

    // ===================================================================
    // processNextRequest
    // ===================================================================

    @Test
    void testProcessNextRequestShowsTask() throws Exception {
        requestQueue.offer(buildShowTask("prompt", new CompletableFuture<>(), () -> {}));
        processNextOnEDT();
        assertEquals(1, permissionPanel.showRequestCount());
        assertTrue(isRequestShowing());
        assertTrue(requestQueue.isEmpty());
    }

    @Test
    void testProcessNextRequestDoesNothingWhenQueueEmpty() throws Exception {
        processNextOnEDT();
        assertEquals(0, permissionPanel.showRequestCount());
        assertFalse(isRequestShowing());
    }

    // ===================================================================
    // Queue: sequential processing
    // ===================================================================

    @Test
    void testSecondTaskQueuedWhileFirstShows() throws Exception {
        requestQueue.offer(buildShowTask("r1", new CompletableFuture<>(), () -> {}));
        processNextOnEDT();
        requestQueue.offer(buildShowTask("r2", new CompletableFuture<>(), () -> {}));
        assertEquals(1, permissionPanel.showRequestCount());
        assertEquals(1, requestQueue.size());
        assertTrue(isRequestShowing());
    }

    @Test
    void testQueuedTaskShowsAfterFirstCompletes() throws Exception {
        requestQueue.offer(buildShowTask("r1", new CompletableFuture<>(), () -> {}));
        processNextOnEDT();
        requestQueue.offer(buildShowTask("r2", new CompletableFuture<>(), () -> {}));
        assertEquals(1, permissionPanel.showRequestCount());

        onEDT(() -> manager.addResultToChat("r1 done", true));
        assertEquals(2, permissionPanel.showRequestCount());
        assertEquals(0, requestQueue.size());
    }

    @Test
    void testThreeTasksProcessSequentially() throws Exception {
        requestQueue.offer(buildShowTask("r1", new CompletableFuture<>(), () -> {}));
        processNextOnEDT();
        requestQueue.offer(buildShowTask("r2", new CompletableFuture<>(), () -> {}));
        requestQueue.offer(buildShowTask("r3", new CompletableFuture<>(), () -> {}));
        assertEquals(1, permissionPanel.showRequestCount());
        assertEquals(2, requestQueue.size());

        onEDT(() -> manager.addResultToChat("r1", true));
        assertEquals(2, permissionPanel.showRequestCount());
        assertEquals(1, requestQueue.size());

        onEDT(() -> manager.addResultToChat("r2", true));
        assertEquals(3, permissionPanel.showRequestCount());
        assertEquals(0, requestQueue.size());
    }

    @Test
    void testIsRequestShowingClearedAfterQueueDrains() throws Exception {
        requestQueue.offer(buildShowTask("r1", new CompletableFuture<>(), () -> {}));
        processNextOnEDT();
        assertTrue(isRequestShowing());
        onEDT(() -> manager.addResultToChat("done", true));
        assertFalse(isRequestShowing());
    }

    // ===================================================================
    // addResultToChat forwarding
    // ===================================================================

    @Test
    void testAddResultToChatForwardsAllowed() throws Exception {
        onEDT(() -> manager.addResultToChat("allowed", true));
        assertEquals(1, chatPanel.results.size());
        assertEquals("allowed", chatPanel.results.get(0));
        assertTrue(chatPanel.allowedList.get(0));
    }

    @Test
    void testAddResultToChatForwardsRejected() throws Exception {
        onEDT(() -> manager.addResultToChat("rejected", false));
        assertEquals(1, chatPanel.results.size());
        assertEquals("rejected", chatPanel.results.get(0));
        assertFalse(chatPanel.allowedList.get(0));
    }

    // ===================================================================
    // Error handling
    // ===================================================================

    @Test
    void testExceptionInShowTaskRejectedByInternalCatch() throws Exception {
        // The showTask created by handlePermissionRequest has an internal
        // catch block. Verify by using handlePermissionRequest with a
        // panel mock that throws.
        CompletableFuture<String> response = new CompletableFuture<>();

        // Replace the panel with one that throws
        PermissionRequestPanel throwingPanel = mock(PermissionRequestPanel.class);
        doAnswer(inv -> { throw new RuntimeException("simulated error"); })
                .when(throwingPanel).showRequest(anyString(), any(), any(), any());
        Field panelField = PermissionDialogManager.class.getDeclaredField("permissionPanel");
        panelField.setAccessible(true);
        panelField.set(manager, throwingPanel);

        manager.handlePermissionRequest(TEST_SESSION, EMPTY_PARAMS, response, () -> {});
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);

        assertTrue(response.isDone());
        assertEquals("reject", response.get());
    }

    // ===================================================================
    // handlePermissionRequest EDT dispatch
    // ===================================================================

    @Test
    void testHandlePermissionRequestPostsToEDT() throws Exception {
        CompletableFuture<String> response = new CompletableFuture<>();
        manager.handlePermissionRequest(TEST_SESSION, EMPTY_PARAMS, response, () -> {});

        // Before EDT: nothing should have happened
        assertEquals(0, requestQueue.size());
        assertFalse(isRequestShowing());
        assertEquals(0, permissionPanel.showRequestCount());

        // Pump EDT
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> latch.countDown());
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // After EDT: request should be showing
        assertTrue(isRequestShowing());
        assertEquals(1, permissionPanel.showRequestCount());
    }
}
