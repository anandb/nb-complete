package github.anandb.netbeans.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;

import github.anandb.netbeans.support.PluginSettings;

/**
 * Shared popup plumbing for explorer-tree context menus (Inspector, Test
 * Results). Row-selects the node under the cursor, builds a menu with
 * <b>Copy</b> and (behind the "Enable context menu additions" flag)
 * <b>Send to Assistant</b> plus caller-supplied extra items, and shows it.
 */
final class TreePopupSupport {

    private TreePopupSupport() {
    }

    /**
     * @param e the mouse event (only popup triggers are processed)
     * @param tree the tree the menu is shown on
     * @param textBuilder converts the selected nodes into the text for
     *        Copy / Send to Assistant
     * @param extraItems optional builder for additional menu items, invoked
     *        only when the context-menu additions flag is enabled
     */
    static void showPopup(MouseEvent e, JTree tree,
            Function<Node[], String> textBuilder, Consumer<JPopupMenu> extraItems) {
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
        final String text = textBuilder.apply(selected);
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
                    SwingUtilities.invokeLater(() -> AssistantTarget.showWithText(text));
                }
            });
            if (extraItems != null) {
                extraItems.accept(menu);
            }
        }
        menu.show(tree, e.getX(), e.getY());
    }
}
