package github.anandb.netbeans.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.JToggleButton;
import javax.swing.table.AbstractTableModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionItemsCopyButtonTest {

    @Test
    void formatsDescriptionsWithOptionalFileAndLocationDetails() {
        List<ActionItemsCopyButton.TaskDetails> items = List.of(
                new ActionItemsCopyButton.TaskDetails(" Fix null check ", "Main.java", "src/Main.java:12"),
                new ActionItemsCopyButton.TaskDetails("Update docs", null, null));

        assertEquals("- Fix null check\n  File: Main.java\n  Location: src/Main.java:12"
                + "\n\n- Update docs", ActionItemsCopyButton.formatItems(items));
    }

    @Test
    void emptyOrHeaderOnlyItemsProduceNoClipboardText() {
        assertEquals("", ActionItemsCopyButton.formatItems(List.of()));
        assertEquals("", ActionItemsCopyButton.formatItems(List.of(
                new ActionItemsCopyButton.TaskDetails("  ", null, null))));
    }

    @Test
    void readsTasksFromTheActiveListInsteadOfVisibleFoldedRows() throws Exception {
        List<String> allTasks = List.of("collapsed item one", "collapsed item two");
        FoldedModel model = new FoldedModel(new FakeTaskList(allTasks));

        assertEquals(1, model.getRowCount(), "the collapsed category exposes only its header row");
        assertEquals("Category header", model.getValueAt(0, 0));
        assertEquals(allTasks, ActionItemsCopyButton.extractTaskObjects(model),
                "copy source includes tasks hidden by the collapsed group and not its header");
    }

    @Test
    void unsupportedModelDoesNotYieldTaskRows() {
        assertThrows(NoSuchFieldException.class,
                () -> ActionItemsCopyButton.extractTaskObjects(new AbstractTableModel() {
                    @Override
                    public int getRowCount() {
                        return 0;
                    }

                    @Override
                    public int getColumnCount() {
                        return 1;
                    }

                    @Override
                    public Object getValueAt(int rowIndex, int columnIndex) {
                        return null;
                    }
                }));
    }

    @Test
    void toolbarInjectionIsAfterGroupToggleAndIdempotent() {
        JToolBar toolbar = new JToolBar(JToolBar.VERTICAL);
        JToggleButton groupActions = new JToggleButton("Group Actions");
        toolbar.add(groupActions);
        AtomicInteger clicks = new AtomicInteger();

        JButton button = ActionItemsCopyButton.addCopyButton(toolbar, clicks::incrementAndGet);
        assertEquals("", button.getText(), "Copy All should be icon-only");
        assertNotNull(button.getIcon(), "Copy All should have a visible icon");
        assertEquals("Copy all Action Items in the current scope and filters", button.getToolTipText());
        assertEquals(button.getToolTipText(), button.getAccessibleContext().getAccessibleName());
        assertEquals(button.getToolTipText(), button.getAccessibleContext().getAccessibleDescription());
        assertEquals(0, toolbar.getComponentIndex(groupActions));
        assertEquals(1, toolbar.getComponentIndex(button));
        assertEquals(button, ActionItemsCopyButton.addCopyButton(toolbar, clicks::incrementAndGet));
        assertEquals(2, toolbar.getComponentCount());

        button.doClick();
        assertEquals(1, clicks.get());
        ActionItemsCopyButton.removeInjectedButtons(toolbar);
        assertEquals(1, toolbar.getComponentCount(), "cleanup preserves the original control");
        assertEquals(groupActions, toolbar.getComponent(0));
    }

    @Test
    void unexpectedToolbarDoesNotReceivePartialControl() {
        JToolBar toolbar = new JToolBar();
        toolbar.add(new JButton("Unexpected"));

        assertNull(ActionItemsCopyButton.addCopyButton(toolbar, () -> {}));
        assertEquals(1, toolbar.getComponentCount());
    }

    private static final class FoldedModel extends AbstractTableModel {
        private final FakeTaskList taskList;

        FoldedModel(FakeTaskList taskList) {
            this.taskList = taskList;
        }

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return "Category header";
        }
    }

    public static final class FakeTaskList {
        private final List<?> tasks;

        FakeTaskList(List<?> tasks) {
            this.tasks = tasks;
        }

        public List<?> getTasks() {
            return tasks;
        }
    }
}
