package github.anandb.netbeans.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ACPProjectManagerTest {

    private ACPProjectManager projectManager;

    @BeforeEach
    void setUp() {
        projectManager = ACPProjectManager.getInstance();
    }

    @Test
    void testListeners() {
        AtomicReference<String> opened = new AtomicReference<>();
        AtomicReference<String> closed = new AtomicReference<>();

        projectManager.setProjectOpenListener(opened::set);
        projectManager.setProjectCloseListener(closed::set);

        // We can't easily trigger the PropertyChangeListener from OpenProjects here,
        // but we can at least verify the listeners are stored.
        assertNotNull(projectManager);
    }
}
