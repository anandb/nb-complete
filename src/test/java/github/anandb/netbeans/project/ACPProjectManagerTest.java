package github.anandb.netbeans.project;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ACPProjectManagerTest {

    /** Fake source: mutable project list, recorded listeners, and a path table. */
    private static final class FakeSource implements OpenProjectsSource {
        private Project[] projects = new Project[0];
        private final List<PropertyChangeListener> listeners = new CopyOnWriteArrayList<>();
        private final Map<Project, String> paths = new HashMap<>();

        @Override
        public Project[] getOpenProjects() {
            return projects;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            listeners.remove(listener);
        }

        @Override
        public String nativePath(Project project) {
            return paths.get(project);
        }

        void setProjects(Project... projects) {
            this.projects = projects;
        }

        void fire(String property) {
            PropertyChangeEvent event = new PropertyChangeEvent(this, property, null, null);
            for (PropertyChangeListener listener : listeners) {
                listener.propertyChange(event);
            }
        }
    }

    private FakeSource source;
    private ACPProjectManager manager;
    private Project a;
    private Project b;
    private List<String> opened;
    private List<String> closed;

    @BeforeEach
    void setUp() {
        source = new FakeSource();
        manager = new ACPProjectManager(source);
        a = mock(Project.class);
        b = mock(Project.class);
        source.paths.put(a, "/tmp/a");
        source.paths.put(b, "/tmp/b");
        opened = new ArrayList<>();
        closed = new ArrayList<>();
    }

    @Test
    void startRegistersListenerAndLoadsCurrentProjects() {
        source.setProjects(a);

        manager.start();

        assertEquals(1, source.listeners.size());
        assertArrayEquals(new Project[] {a}, manager.getAllOpenProjects());
    }

    @Test
    void openProjectFiresOpenListener() {
        manager.start();
        manager.setProjectOpenListener(opened::add);
        source.setProjects(a);

        manager.syncActiveProject();

        assertEquals(List.of("/tmp/a"), opened);
        assertTrue(closed.isEmpty());
    }

    @Test
    void closeProjectFiresCloseListener() {
        source.setProjects(a);
        manager.start();
        manager.setProjectCloseListener(closed::add);
        source.setProjects();

        manager.syncActiveProject();

        assertEquals(List.of("/tmp/a"), closed);
        assertTrue(opened.isEmpty());
    }

    @Test
    void openListenerReplaysAlreadyOpenProjects() {
        source.setProjects(a, b);
        manager.start();

        manager.setProjectOpenListener(opened::add);

        assertEquals(List.of("/tmp/a", "/tmp/b"), opened);
    }

    @Test
    void openListenerSkipsNullProjectsOnReplay() {
        source.setProjects(null, a);
        manager.start();

        manager.setProjectOpenListener(opened::add);

        assertEquals(List.of("/tmp/a"), opened);
    }

    @Test
    void propertyChangeSchedulesCoalescedSync() {
        manager.start();
        manager.setProjectOpenListener(opened::add);
        source.setProjects(a);

        source.fire(OpenProjects.PROPERTY_OPEN_PROJECTS);

        await().atMost(2, TimeUnit.SECONDS).until(() -> opened.contains("/tmp/a"));
    }

    @Test
    void isProjectEventMatchesOnlyProjectProperties() {
        assertTrue(ACPProjectManager.isProjectEvent(OpenProjects.PROPERTY_OPEN_PROJECTS));
        assertTrue(ACPProjectManager.isProjectEvent(OpenProjects.PROPERTY_MAIN_PROJECT));
        assertFalse(ACPProjectManager.isProjectEvent("some.other.property"));
        assertFalse(ACPProjectManager.isProjectEvent(null));
    }

    @Test
    void getAllOpenProjectsReturnsDefensiveCopy() {
        source.setProjects(a);
        manager.start();

        Project[] first = manager.getAllOpenProjects();
        first[0] = b;

        assertSame(a, manager.getAllOpenProjects()[0]);
    }

    @Test
    void getAllOpenProjectsReturnsEmptyWhenNoProjects() {
        manager.start();

        assertEquals(0, manager.getAllOpenProjects().length);
    }

    @Test
    void syncWithSameProjectsFiresNoListener() {
        source.setProjects(a);
        manager.start();
        manager.setProjectOpenListener(opened::add);
        manager.setProjectCloseListener(closed::add);
        // Registration replays the already-open project; drop that replay.
        opened.clear();
        closed.clear();

        manager.syncActiveProject();

        assertTrue(opened.isEmpty());
        assertTrue(closed.isEmpty());
    }
}
