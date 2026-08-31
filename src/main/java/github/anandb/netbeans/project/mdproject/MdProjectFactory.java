package github.anandb.netbeans.project.mdproject;

import java.io.IOException;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

import github.anandb.netbeans.support.PluginSettings;

/**
 * {@link ProjectFactory} that recognises a folder as a project when it
 * contains a {@code .mdproject} marker file.
 *
 * <p>Gated by the {@code actions.markdownProject} preference (default on):
 * when disabled, the factory declines all directories so no Markdown
 * projects are recognised. Already-open projects are unaffected.
 */
@ServiceProvider(service = ProjectFactory.class)
public final class MdProjectFactory implements ProjectFactory {

    static final String MARKER = ".mdproject";

    @Override
    public boolean isProject(FileObject projectDirectory) {
        if (!PluginSettings.isMarkdownProjectEnabled()) {
            return false;
        }
        return projectDirectory.getFileObject(MARKER) != null;
    }

    @Override
    public Project loadProject(FileObject dir, ProjectState state) throws IOException {
        if (!isProject(dir)) {
            return null;
        }
        return new MdProject(dir);
    }

    @Override
    public void saveProject(Project project) throws IOException {
        FileObject dir = project.getProjectDirectory();
        if (dir.getFileObject(MARKER) == null) {
            FileObject marker = dir.createData(MARKER);
            marker.setAttribute("displayName", dir.getNameExt());
        }
    }
}
