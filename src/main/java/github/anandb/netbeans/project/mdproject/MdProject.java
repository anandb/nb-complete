package github.anandb.netbeans.project.mdproject;

import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * A simple project type identified by a {@code .mdproject} marker file.
 * Used for projects consisting primarily of markdown and other text files.
 */
public final class MdProject implements Project {

    private final FileObject dir;
    private final Lookup lookup;

    MdProject(FileObject dir) {
        this.dir = dir;
        MdProjectInformation info = new MdProjectInformation(this);
        MdLogicalViewProvider view = new MdLogicalViewProvider(this);
        MdProjectConfigurationProvider configProvider = new MdProjectConfigurationProvider();
        this.lookup = Lookups.fixed(new Object[]{
            this, info, view, configProvider
        });
    }

    @Override
    public FileObject getProjectDirectory() {
        return dir;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }
}
