package github.anandb.netbeans.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.openide.filesystems.FileUtil;
import org.openide.modules.OnStart;
import org.openide.nodes.Node;
import org.openide.text.PositionBounds;
import org.openide.util.Exceptions;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Adds a right-click context menu to the Inspector window (inspections results,
 * "AnalysisResultTopComponent"). The IDE module cannot be modified, so this hooks
 * the Inspector's tree at runtime and installs its own popup with:
 * <ul>
 * <li><b>Copy</b> - copies the selected subtree (filename, line and description
 * of each problem) to the clipboard as text.</li>
 * <li><b>Send to Assistant</b> - sends the same text to the mini assistant input.</li>
 * </ul>
 */
@OnStart
public class InspectorContextMenu implements Runnable, PropertyChangeListener {

    private static final String INSPECTOR_TC_ID = "AnalysisResultTopComponent"; // NOI18N

    /** Trees we already hooked, keyed weakly so we never double-install. */
    private static final Set<JTree> HOOKED = Collections.newSetFromMap(new WeakHashMap<JTree, Boolean>());

    private boolean registered;

    @Override
    public void run() {
        if (!registered) {
            registered = true;
            TopComponent.getRegistry().addPropertyChangeListener(this);
            // @OnStart runs on a non-EDT startup thread; defer the component
            // tree access to the EDT.
            SwingUtilities.invokeLater(this::hookIfOpen);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (TopComponent.Registry.PROP_OPENED.equals(evt.getPropertyName())) {
            hookIfOpen();
        }
    }

    /** Retry interval and max attempts when the Inspector tree is not yet built. */
    private static final int HOOK_RETRY_MS = 250;
    private static final int HOOK_RETRY_MAX = 20;

    private void hookIfOpen() {
        TopComponent tc = WindowManager.getDefault().findTopComponent(INSPECTOR_TC_ID);
        if (tc != null && tc.isOpened()) {
            JTree tree = findTree(tc);
            if (tree != null && HOOKED.add(tree)) {
                tree.addMouseListener(new PopupHandler(tree));
            } else if (tree == null) {
                // Tree may be populated asynchronously after the window opens;
                // retry a few times instead of missing the hook entirely.
                retryHook(tc, 0);
            }
        }
    }

    private void retryHook(TopComponent tc, int attempt) {
        if (!tc.isOpened() || attempt >= HOOK_RETRY_MAX) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JTree tree = findTree(tc);
            if (tree != null) {
                if (HOOKED.add(tree)) {
                    tree.addMouseListener(new PopupHandler(tree));
                }
            } else {
                Timer timer = new Timer(HOOK_RETRY_MS, e -> retryHook(tc, attempt + 1));
                timer.setRepeats(false);
                timer.start();
            }
        });
    }

    private static JTree findTree(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JTree) {
                return (JTree) comp;
            }
            if (comp instanceof Container) {
                JTree found = findTree((Container) comp);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Popup trigger that shows Copy / Send to Assistant for the node under the cursor. */
    private static final class PopupHandler extends MouseAdapter {

        private final JTree tree;

        PopupHandler(JTree tree) {
            this.tree = tree;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        private void maybeShowPopup(MouseEvent e) {
            TreePopupSupport.showPopup(e, tree, InspectorContextMenu::extractSubtreeText, null);
        }
    }

    /** Walks each selected node's subtree and renders problems as {@code file:line: description}. */
    static String extractSubtreeText(Node[] nodes) {
        StringBuilder sb = new StringBuilder();
        for (Node node : nodes) {
            extract(node, sb);
        }
        return sb.toString();
    }

    private static void extract(Node node, StringBuilder sb) {
        ErrorDescription ed = node.getLookup().lookup(ErrorDescription.class);
        if (ed != null) {
            String file = ed.getFile() != null ? FileUtil.getFileDisplayName(ed.getFile()) : ""; // NOI18N
            String line = "";
            try {
                PositionBounds range = ed.getRange();
                if (range != null && range.getBegin() != null) {
                    line = Integer.toString(range.getBegin().getLine() + 1);
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            String desc = ed.getDescription();
            if (desc != null) {
                sb.append(file).append(':').append(line).append(": ").append(desc).append('\n'); // NOI18N
            }
            return;
        }
        Node[] children = node.getChildren().getNodes(true);
        if (children.length == 0) {
            sb.append(node.getDisplayName()).append('\n'); // NOI18N
            return;
        }
        for (Node child : children) {
            extract(child, sb);
        }
    }
}
