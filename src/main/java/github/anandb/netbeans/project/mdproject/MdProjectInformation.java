package github.anandb.netbeans.project.mdproject;

import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.openide.util.ImageUtilities;

/**
 * {@link ProjectInformation} for a {@link MdProject}.
 */
public final class MdProjectInformation implements ProjectInformation {

    private static final String ICON_PATH = "github/anandb/netbeans/ui/icons/mdproject.svg";

    private final MdProject project;
    private final Icon icon;

    MdProjectInformation(MdProject project) {
        this.project = project;
        this.icon = new ImageIcon(ImageUtilities.loadImage(ICON_PATH));
    }

    @Override
    public String getName() {
        return project.getProjectDirectory().getNameExt();
    }

    @Override
    public String getDisplayName() {
        return project.getProjectDirectory().getNameExt();
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public Project getProject() {
        return project;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        // no-op — properties are static
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        // no-op
    }
}
