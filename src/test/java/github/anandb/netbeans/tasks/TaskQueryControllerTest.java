package github.anandb.netbeans.tasks;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import github.anandb.netbeans.tasks.TasksModel.TaskQuery;
import github.anandb.netbeans.tasks.TasksModel.TaskRepository;
import org.netbeans.modules.bugtracking.spi.QueryController;

class TaskQueryControllerTest {

    @Test
    void providesOnlyEditMode() {
        TaskQuery query = new TaskQuery("All tasks", new TaskRepository());
        TaskQueryController controller = new TaskQueryController(null, query);
        assertTrue(controller.providesMode(QueryController.QueryMode.EDIT));
        assertFalse(controller.providesMode(QueryController.QueryMode.VIEW));
    }

    @Test
    void tagFilterRoundTrips() {
        TaskQuery query = new TaskQuery("All tasks", new TaskRepository());
        assertTrue(query.getTagFilter().isEmpty());
        query.setTagFilter(Set.of("bug", "urgent"));
        assertEquals(Set.of("bug", "urgent"), query.getTagFilter());
        query.setTagFilter(null);
        assertTrue(query.getTagFilter().isEmpty());
    }
}
