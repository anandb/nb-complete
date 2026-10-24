package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;

import github.anandb.netbeans.ui.history.FakeLogEntries;

import java.util.List;

import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.DefaultListModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Message recovery and popup injection for the Show History "Copy Message"
 * item. The versioning module's list items are absent from the test classpath,
 * so stand-ins mirror their shape: a package-private {@code getUserData()}
 * returning either a log entry with a message or a file/event with none.
 */
class ShowHistoryCopyMessageHookTest {

    /** Mirror of AbstractSummaryView.Item: package-private user-data accessor. */
    private static final class FakeItem {
        private final Object userData;

        FakeItem(Object userData) {
            this.userData = userData;
        }

        @SuppressWarnings("unused") // reached reflectively, like the real Item
        Object getUserData() {
            return userData;
        }
    }

    @Test
    void commitRowsYieldTheirMessagesAndFileRowsAreSkipped() {
        JList<Object> list = listOf(
                new FakeItem(FakeLogEntries.commit("first commit")),
                new FakeItem(FakeLogEntries.file()),
                new FakeItem(FakeLogEntries.commit("third commit")));
        list.setSelectionInterval(0, 2);

        assertEquals(List.of("first commit", "third commit"),
                ShowHistoryCopyMessageHook.selectedMessages(list));
    }

    @Test
    void duplicateMessagesCollapse() {
        JList<Object> list = listOf(
                new FakeItem(FakeLogEntries.commit("same")),
                new FakeItem(FakeLogEntries.commit("same")));
        list.setSelectionInterval(0, 1);

        assertEquals(List.of("same"), ShowHistoryCopyMessageHook.selectedMessages(list));
    }

    @Test
    void messageOfReturnsNullWhenThereIsNoUserDataOrMessage() {
        assertNull(ShowHistoryCopyMessageHook.messageOf(null));
        assertNull(ShowHistoryCopyMessageHook.messageOf(new Object()));
        assertNull(ShowHistoryCopyMessageHook.messageOf(new FakeItem(FakeLogEntries.file())));
        assertEquals("msg", ShowHistoryCopyMessageHook.messageOf(new FakeItem(FakeLogEntries.commit("msg"))));
    }

    @Test
    void injectAddsOneItemAfterASeparator() {
        JPopupMenu popup = new JPopupMenu();
        popup.add(new JMenuItem("Diff Previous"));
        String label = NbBundle.getMessage(ShowHistoryCopyMessageHook.class, "CTL_CopyMessage");

        ShowHistoryCopyMessageHook.inject(popup, List.of("first commit"));

        JMenuItem copy = findItem(popup, label);
        assertNotNull(copy, "Copy Message item injected");
        assertTrue(popup.getComponent(popup.getComponentCount() - 2) instanceof JPopupMenu.Separator,
                "a separator precedes the item");
        assertEquals(3, popup.getComponentCount());

        ShowHistoryCopyMessageHook.inject(popup, List.of("first commit"));
        assertEquals(3, popup.getComponentCount(), "injection is idempotent per popup");
    }

    @Test
    void injectDoesNothingForFileRows() {
        JPopupMenu popup = new JPopupMenu();
        popup.add(new JMenuItem("Diff Previous"));

        ShowHistoryCopyMessageHook.inject(popup, List.of());

        assertEquals(1, popup.getComponentCount(), "no item for a non-commit row");
    }

    private static JMenuItem findItem(JPopupMenu popup, String text) {
        for (java.awt.Component component : popup.getComponents()) {
            if (component instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        return null;
    }

    private static JList<Object> listOf(Object... values) {
        DefaultListModel<Object> model = new DefaultListModel<>();
        for (Object value : values) {
            model.addElement(value);
        }
        return new JList<>(model);
    }
}
