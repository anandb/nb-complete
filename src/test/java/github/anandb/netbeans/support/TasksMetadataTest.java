package github.anandb.netbeans.support;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TasksMetadataTest {

    @Test
    void storesReadsListsAndRemovesMetadata() {
        String first = "test-" + UUID.randomUUID();
        String second = "test-" + UUID.randomUUID();
        try {
            TasksMetadata.put(second, "/tmp/b.txt", "B");
            TasksMetadata.put(first, "/tmp/a.txt", "A");

            assertEquals("/tmp/a.txt", TasksMetadata.tasksPathOf(first));
            assertEquals("A", TasksMetadata.displayNameOf(first));
            List<String> ids = TasksMetadata.allIds();
            assertTrue(ids.contains(first));
            assertTrue(ids.contains(second));
            assertEquals(ids.stream().sorted().toList(), ids, "ids must be sorted");
        } finally {
            TasksMetadata.remove(first);
            TasksMetadata.remove(second);
        }
        assertFalse(TasksMetadata.allIds().contains(first));
        assertEquals("", TasksMetadata.tasksPathOf(first));
        assertEquals("", TasksMetadata.displayNameOf(first));
    }

    @Test
    void nullValuesAreStoredAsEmptyStrings() {
        String id = "test-" + UUID.randomUUID();
        try {
            TasksMetadata.put(id, null, null);
            assertEquals("", TasksMetadata.tasksPathOf(id));
            assertEquals("", TasksMetadata.displayNameOf(id));
        } finally {
            TasksMetadata.remove(id);
        }
    }

    @Test
    void nullRepoIdFallsBackToTheEmptyKey() {
        try {
            TasksMetadata.put(null, "/tmp/null.txt", "Null");
            assertEquals("/tmp/null.txt", TasksMetadata.tasksPathOf(null));
            assertEquals("Null", TasksMetadata.displayNameOf(null));
        } finally {
            TasksMetadata.remove(null);
        }
    }
}
