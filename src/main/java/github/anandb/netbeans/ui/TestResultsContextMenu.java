package github.anandb.netbeans.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.AbstractAction;
import javax.swing.JEditorPane;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.explorer.ExplorerManager;
import org.openide.modules.OnStart;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import github.anandb.netbeans.support.PluginSettings;

/**
 * Adds a right-click context menu to the Test Results window
 * ("gsf-testrunner-results" TopComponent, the JUnit/TestNG results window).
 * The IDE module cannot be modified, so this hooks the results tree at runtime
 * and installs its own popup with:
 * <ul>
 * <li><b>Copy</b> - copies the selected node subtree (suite/method/stack frames)
 * to the clipboard as text.</li>
 * <li><b>Send to Assistant</b> - sends the same text to the mini assistant input.</li>
 * <li><b>Send Full Output</b> - sends the entire captured output of the selected
 * test session (right-hand pane) to the mini assistant input.</li>
 * </ul>
 * The window is a JTabbedPane (one tab per test session) where each tab is a
 * JSplitPane: left = results tree (BeanTreeView), right = output JEditorPane.
 */
@OnStart
public class TestResultsContextMenu implements Runnable, PropertyChangeListener, ChangeListener {

    private static final String RESULTS_TC_ID = "gsf-testrunner-results"; // NOI18N

    /** Trees we already hooked, keyed weakly so we never double-install. */
    private static final Set<JTree> HOOKED = java.util.Collections.newSetFromMap(new WeakHashMap<JTree, Boolean>());

    private boolean registered;
    private JTabbedPane tabPane;

    @Override
    public void run() {
        if (!registered) {
            registered = true;
            TopComponent.getRegistry().addPropertyChangeListener(this);
            hookIfOpen();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (TopComponent.Registry.PROP_OPENED.equals(evt.getPropertyName())) {
            hookIfOpen();
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        hookCurrentTab();
    }

    private void hookIfOpen() {
        TopComponent tc = WindowManager.getDefault().findTopComponent(RESULTS_TC_ID);
        if (tc != null && tc.isOpened()) {
            JTabbedPane pane = findTabbedPane(tc);
            if (pane != null) {
                if (tabPane != pane) {
                    if (tabPane != null) {
                        tabPane.removeChangeListener(this);
                    }
                    tabPane = pane;
                    pane.addChangeListener(this);
                }
                hookCurrentTab();
            }
        }
    }

    /** Hooks the tree of the currently selected tab (new test runs replace tabs). */
    private void hookCurrentTab() {
        if (tabPane == null) {
            return;
        }
        Component tab = tabPane.getSelectedComponent();
        JTree tree = findTree(tab);
        if (tree != null && HOOKED.add(tree)) {
            tree.addMouseListener(new PopupHandler(tree, tab));
        }
    }

    private static JTabbedPane findTabbedPane(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JTabbedPane) {
                return (JTabbedPane) comp;
            }
            if (comp instanceof Container) {
                JTabbedPane found = findTabbedPane((Container) comp);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JTree findTree(Component comp) {
        if (comp instanceof JTree) {
            return (JTree) comp;
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                JTree found = findTree(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JEditorPane findEditorPane(Component comp) {
        if (comp instanceof JEditorPane) {
            return (JEditorPane) comp;
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                JEditorPane found = findEditorPane(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Popup trigger that shows Copy / Send to Assistant for the selected test nodes. */
    private static final class PopupHandler extends MouseAdapter {

        private final JTree tree;
        private final Component tab;

        PopupHandler(JTree tree, Component tab) {
            this.tree = tree;
            this.tab = tab;
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
            if (!e.isPopupTrigger()) {
                return;
            }
            int row = tree.getRowForLocation(e.getX(), e.getY());
            if (row != -1 && !tree.isRowSelected(row)) {
                tree.setSelectionRow(row);
            }
            Node[] selected = ExplorerManager.find(tree).getSelectedNodes();
            if (selected.length == 0) {
                return;
            }
            final String text = extractSubtreeText(selected);
            final JEditorPane outputPane = findEditorPane(tab);
            final String fullOutput = outputPane != null ? outputPane.getText() : null;

            final JPopupMenu menu = new JPopupMenu();
            menu.add(new AbstractAction("Copy") {
                @Override public void actionPerformed(ActionEvent ev) {
                    Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clip.setContents(new StringSelection(text), null);
                }
            });
            if (PluginSettings.isSortLinesEnabled()) {
                menu.add(new AbstractAction("\uD83D\uDCAC Send to Assistant") {
                    @Override public void actionPerformed(ActionEvent ev) {
                        SwingUtilities.invokeLater(() -> MiniAssistantDialog.getInstance().showWithText(text));
                    }
                });
                if (fullOutput != null && !fullOutput.isBlank()) {
                    menu.add(new AbstractAction("\uD83D\uDCAC Send Full Output") {
                        @Override public void actionPerformed(ActionEvent ev) {
                            SwingUtilities.invokeLater(() -> MiniAssistantDialog.getInstance().showWithText(fullOutput));
                        }
                    });
                }
            }
            menu.show(tree, e.getX(), e.getY());
        }
    }

    /** Renders each selected node as its display name plus indented child frames. */
    static String extractSubtreeText(Node[] nodes) {
        StringBuilder sb = new StringBuilder();
        for (Node node : nodes) {
            appendNode(node, sb, 0);
        }
        return sb.toString();
    }

    private static void appendNode(Node node, StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append(node.getDisplayName()).append('\n'); // NOI18N
        for (Node child : node.getChildren().getNodes(true)) {
            appendNode(child, sb, depth + 1);
        }
    }
}
