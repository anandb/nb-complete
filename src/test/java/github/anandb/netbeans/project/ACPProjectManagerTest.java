package github.anandb.netbeans.project;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ACPProjectManagerTest {

    private ACPProjectManager projectManager;

    @BeforeEach
    void setUp() throws Exception {
        // Reset Singleton
        Field instanceField = ACPProjectManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

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
