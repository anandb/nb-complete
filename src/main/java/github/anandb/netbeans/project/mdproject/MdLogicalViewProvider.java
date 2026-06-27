package github.anandb.netbeans.project.mdproject;

import java.util.regex.Pattern;
import javax.swing.Action;
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
 * file tree in the Projects tab. Hides OS junk files and common
 * temporary/editor swap files via {@link #FILE_FILTER}.
 */
public final class MdLogicalViewProvider implements LogicalViewProvider {

    private static final String ICON = "github/anandb/netbeans/ui/icons/mdproject.svg";

    /** Files matching this name pattern are hidden from the project tree. */
    private static final Pattern HIDDEN_NAMES = Pattern.compile(
        "(?i)^\\.(?!gitignore|gitattributes|editorconfig|npmrc)"  // dotfiles except common ones
        + "|^Thumbs\\.db$|^Desktop\\.ini$|^\\.DS_Store$"
        + "|\\.(?:swp|swo|bak|~)$"                                  // editor swap/backup
    );

    private static final DataFilter FILE_FILTER = (DataObject dob) -> {
        FileObject fo = dob.getPrimaryFile();
        return !HIDDEN_NAMES.matcher(fo.getNameExt()).find();
    };

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
            children = df.createNodeChildren(FILE_FILTER);
        } catch (Exception e) {
            // fallback to leaf node
        }
        AbstractNode root = new AbstractNode(children, Lookups.fixed(project, dir)) {
            @Override
            public Action[] getActions(boolean context) {
                return new Action[] {
                    CommonProjectActions.closeProjectAction(),
                    CommonProjectActions.deleteProjectAction(),
                };
            }
        };
        root.setDisplayName(project.getProjectDirectory().getNameExt());
        root.setIconBaseWithExtension(ICON);
        return root;
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
