package github.anandb.netbeans.tasks;

import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.TaskRecord;
import github.anandb.netbeans.model.TaskStatus;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard: the Beanbot task editor ({@link TaskIssueController}) must
 * build entirely on its own, using only local Swing components and enums. It
 * must NOT transitively construct the NetBeans Bugtracking
 * {@code IssueTopComponent} or load any remote image (the EDT freeze reported
 * in a.txt was caused by {@code IssueTopComponent}/{@code FindBar} blocking on
 * {@code MediaTracker} while remote images were fetched). Opening a task in the
 * Dashboard still routes through the bugtracking SPI, but our component itself
 * stays local and headless-safe.
 */
class TaskIssueControllerBuildTest {

    private static TaskRecord rec(String id, String status, String summary) {
        return new TaskRecord(id, status, "normal", summary, "desc", "", "bug, help wanted", "",
            List.of(), "2026-08-01T00:00:00Z", "2026-08-01T00:00:00Z");
    }

    @Test
    void buildsLocalComponentWithoutBugtrackingUi() {
        TaskIssueProvider provider = new TaskIssueProvider();
        TaskIssue issue = new TaskIssue("r1", rec("t-1", "open", "Investigate freeze"));
        // store == null is fine for build/populate; only saveChanges touches it.
        TaskIssueController ctrl = new TaskIssueController(null, provider, issue);

        JComponent component = ctrl.getComponent();

        assertNotNull(component, "issue editor component must build");
        assertInstanceOf(JPanel.class, component,
            "our editor is a plain local JPanel, not the bugtracking IssueTopComponent");
        // Must not depend on a remote icon: a JPanel has no ImageIcon field to hang on.
        assertNull(component.getClientProperty("ISSUE_TOP_COMPONENT"),
            "must not be the bugtracking IssueTopComponent");
    }

    @Test
    void statusComboIsEnumDrivenAndPopulated() {
        TaskIssueProvider provider = new TaskIssueProvider();
        TaskIssue issue = new TaskIssue("r1", rec("t-2", "in-progress", "Edit tags"));
        TaskIssueController ctrl = new TaskIssueController(null, provider, issue);
        ctrl.getComponent();

        // Reach into the built component to confirm the status dropdown is the enum.
        JComboBox<TaskStatus> statusBox = findStatusBox(ctrl.getComponent());
        assertNotNull(statusBox, "status combo must be present");
        assertSame(TaskStatus.IN_PROGRESS, statusBox.getSelectedItem(),
            "existing in-progress task must select the matching enum value");
        assertTrue(statusBox.getItemCount() == TaskStatus.values().length,
            "status combo must offer exactly the enum values");
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<TaskStatus> findStatusBox(JComponent root) {
        if (root instanceof JComboBox<?> cb && cb.getModel().getSize() > 0
                && cb.getModel().getElementAt(0) instanceof TaskStatus) {
            return (JComboBox<TaskStatus>) cb;
        }
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JComponent jc) {
                JComboBox<TaskStatus> found = findStatusBox(jc);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}