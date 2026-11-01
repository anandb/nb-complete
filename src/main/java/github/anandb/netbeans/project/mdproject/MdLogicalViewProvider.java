package github.anandb.netbeans.project.mdproject;

import javax.swing.Action;

import github.anandb.netbeans.pattern.HiddenNamePattern;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.project.ui.support.CommonProjectActions;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFilter;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.lookup.Lookups;

/**
 * {@link LogicalViewProvider} that renders the project directory's full
 * file tree in the Projects tab. Hides OS junk files, common
 * temporary/editor swap files, and user-configured ignored patterns
 * (from {@code .mdproject-ignore}).
 */
public final class MdLogicalViewProvider implements LogicalViewProvider {

    private static final String ICON = "github/anandb/netbeans/ui/icons/mdproject.svg";

    private final MdProject project;

    MdLogicalViewProvider(MdProject project) {
        this.project = project;
    }

    @Override
    public Node createLogicalView() {
        FileObject dir = project.getProjectDirectory();
        Children children = Children.LEAF;
        try {
            DataFolder df = DataFolder.findFolder(dir);
            children = df.createNodeChildren(createFilter());
        } catch (Exception e) {
            // fallback to leaf node
        }
        AbstractNode root = new AbstractNode(children, Lookups.fixed(project, dir)) {
            @Override
            public Action[] getActions(boolean context) {
                return new Action[] {
                    CommonProjectActions.closeProjectAction(),
                };
            }
        };
        root.setDisplayName(project.getProjectDirectory().getNameExt());
        root.setIconBaseWithExtension(ICON);
        return root;
    }

    /** Creates a {@link DataFilter} combining built-in hidden names with user-configured ignores. */
    private DataFilter createFilter() {
        return (DataObject dob) -> {
            FileObject fo = dob.getPrimaryFile();
            if (HiddenNamePattern.isHidden(fo.getNameExt())) {
                return false;
            }
            return !MdProjectIgnoredFiles.isIgnored(project, fo);
        };
    }

    @Override
    public Node findPath(Node root, Object target) {
        if (target instanceof FileObject fo) {
            FileObject dir = project.getProjectDirectory();
            if (fo.equals(dir)) {
                return root;
            }
        }
        return null;
    }
}
