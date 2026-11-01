package github.anandb.netbeans.tasks;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.netbeans.modules.bugtracking.spi.RepositoryInfo;

import github.anandb.netbeans.support.PluginSettings;

/**
 * The Task Repository toggle must gate repository creation, because the
 * bugtracking framework calls {@code createRepository(RepositoryInfo)} during
 * its own startup, racing {@code TasksStartup}.
 */
class TasksConnectorGatingTest {

    @Test
    void disabledRepositoryReturnsNoRepository() {
        RepositoryInfo info = new RepositoryInfo("repo-1", TasksModel.CONNECTOR_ID, "", "Tasks", "");
        try (MockedStatic<PluginSettings> settings = mockStatic(PluginSettings.class)) {
            settings.when(PluginSettings::isTaskRepositoryEnabled).thenReturn(false);

            assertNull(new TasksConnector().createRepository(info));
        }
    }
}
