package github.anandb.netbeans.project.mdproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ProjectConfigurationProvider;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.project.ui.CustomizerProvider2;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

class MdProjectServicesTest {

    @Test
    void projectLookupProvidesProjectUiAndConfigurationServices(@TempDir Path dir) {
        FileObject folder = FileUtil.toFileObject(dir.toFile());
        MdProject project = new MdProject(folder);

        assertSame(folder, project.getProjectDirectory());
        assertSame(project, project.getLookup().lookup(Project.class));
        assertSame(project, project.getLookup().lookup(MdProject.class));
        assertNotNull(project.getLookup().lookup(LogicalViewProvider.class));
        assertNotNull(project.getLookup().lookup(CustomizerProvider2.class));

        MdProjectInformation info = project.getLookup().lookup(MdProjectInformation.class);
        assertSame(project, info.getProject());
        assertEquals(folder.getNameExt(), info.getName());
        assertEquals(folder.getNameExt(), info.getDisplayName());
        assertNotNull(info.getIcon());

        ProjectConfigurationProvider<?> configProvider =
                project.getLookup().lookup(ProjectConfigurationProvider.class);
        assertNotNull(configProvider);
        assertEquals(1, configProvider.getConfigurations().size());
        assertSame(configProvider.getActiveConfiguration(),
                configProvider.getConfigurations().iterator().next());
        assertEquals("default", configProvider.getActiveConfiguration().getDisplayName());
        assertFalse(configProvider.hasCustomizer());
    }

    @Test
    void configurationProviderExposesAnImmutableSingleConfiguration(@TempDir Path dir) {
        MdProject project = new MdProject(FileUtil.toFileObject(dir.toFile()));
        ProjectConfigurationProvider<?> provider =
                project.getLookup().lookup(ProjectConfigurationProvider.class);

        assertEquals(1, provider.getConfigurations().size());
        assertFalse(provider.configurationsAffectAction("build"));
    }
}
