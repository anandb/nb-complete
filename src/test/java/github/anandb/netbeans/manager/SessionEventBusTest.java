package github.anandb.netbeans.manager;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionUpdate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionEventBusTest {

    private static class RecordingListener implements SessionListener {
        private final List<String> events;

        RecordingListener(List<String> events) {
            this.events = events;
        }

        @Override
        public void onSessionListUpdated(List<Session> sessions) {
            events.add("list");
        }

        @Override
        public void onSessionStarted(String sessionId) {
            events.add("started:" + sessionId);
        }

        @Override
        public void onSessionLoaded(String sessionId, List<SessionConfigOption> options, boolean isStartup) {
            events.add("loaded:" + sessionId + ":" + isStartup);
        }

        @Override
        public void onSessionLoading(boolean isLoading) {
            events.add("loading:" + isLoading);
        }

        @Override
        public void onSessionError(String message) {
            events.add("error:" + message);
        }

        @Override
        public void onSessionUpdate(SessionUpdate update) {
            events.add("update");
        }

        @Override
        public void onSessionProgress(int percent) {
            events.add("progress:" + percent);
        }

        @Override
        public void onSessionLoadFailed(String sessionId, String reason) {
            events.add("failed:" + sessionId + ":" + reason);
        }

        @Override
        public void onSessionRenamed(String sessionId) {
            events.add("renamed:" + sessionId);
        }

        @Override
        public void onAllProjectsClosed() {
            events.add("closed");
        }

        @Override
        public void onPreambleStarted(String sessionId) {
            events.add("preamble-started:" + sessionId);
        }

        @Override
        public void onPreambleSkipped(String sessionId) {
            events.add("preamble-skipped:" + sessionId);
        }

        @Override
        public void onPreambleDone() {
            events.add("preamble-done");
        }

        @Override
        public void onInternalMessageSent() {
            events.add("internal-sent");
        }

        @Override
        public void onInternalMessageDone() {
            events.add("internal-done");
        }
    }

    @Test
    void everyEventReachesRegisteredListeners() {
        SessionEventBus bus = new SessionEventBus();
        List<String> events = new ArrayList<>();
        RecordingListener listener = new RecordingListener(events);
        bus.add(listener);

        bus.sessionLoading(true);
        bus.sessionListUpdated(List.of());
        bus.sessionStarted("s1");
        bus.sessionLoaded("s1", List.of(), true);
        bus.sessionProgress(42);
        bus.sessionError("boom");
        bus.sessionLoadFailed("s1", "unknown_execution");
        bus.sessionUpdate(null);
        bus.sessionRenamed("s1");
        bus.allProjectsClosed();
        bus.preambleStarted("s1");
        bus.preambleSkipped("s1");
        bus.preambleDone();
        bus.internalMessageSent();
        bus.internalMessageDone();

        assertEquals(List.of("loading:true", "list", "started:s1", "loaded:s1:true",
                "progress:42", "error:boom", "failed:s1:unknown_execution", "update",
                "renamed:s1", "closed", "preamble-started:s1", "preamble-skipped:s1",
                "preamble-done", "internal-sent", "internal-done"), events);
    }

    @Test
    void removedListenersReceiveNoFurtherEvents() {
        SessionEventBus bus = new SessionEventBus();
        List<String> events = new ArrayList<>();
        RecordingListener listener = new RecordingListener(events);
        bus.add(listener);
        bus.remove(listener);

        bus.sessionLoading(false);

        assertEquals(List.of(), events);
    }

    @Test
    void listenerMayRemoveItselfDuringFanOut() {
        SessionEventBus bus = new SessionEventBus();
        List<String> events = new ArrayList<>();
        RecordingListener listener = new RecordingListener(events) {
            @Override
            public void onSessionLoading(boolean isLoading) {
                super.onSessionLoading(isLoading);
                bus.remove(this);
            }
        };
        bus.add(listener);

        bus.sessionLoading(true);
        bus.sessionLoading(false);

        assertEquals(List.of("loading:true"), events);
    }
}
