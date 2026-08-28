package github.anandb.netbeans.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;

import org.netbeans.modules.bugtracking.spi.IssueScheduleInfo;
import org.netbeans.modules.bugtracking.spi.IssueScheduleProvider;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
import org.openide.util.Lookup;

/**
 * {@link IssueScheduleProvider} mapping the {@code dueDate} column to the Tasks
 * Dashboard "Scheduled" category. Setting a schedule updates the record's
 * due date and persists it through the store.
 */
public final class TaskScheduleProvider implements IssueScheduleProvider<TaskIssue> {

    private TaskRepositoryControl store() {
        return Lookup.getDefault().lookup(TaskRepositoryControl.class);
    }

    @Override
    public void setSchedule(TaskIssue i, IssueScheduleInfo scheduleInfo) {
        TaskRecord r = i.getRecord();
        TaskRepositoryControl s = store();
        // Keep consistent with getSchedule/getDueDate, which report no schedule
        // for finished tasks — never set a due date that would linger and
        // reappear if the task is reopened.
        if (r == null || r.isFinished()
                || scheduleInfo == null || scheduleInfo.getDate() == null) {
            return;
        }
        String dueDate = scheduleInfo.getDate().toInstant().toString();
        TaskRecord updated = new TaskRecord(r.id(), r.status(), r.priority(), r.summary(),
            r.tags(), r.projects(), dueDate, r.estimate(), r.consumed(),
            r.createdAt(), r.completedAt(), r.updatedAt());
        if (s != null) {
            s.update(i.getRepositoryId(), updated);
        }
        i.setRecord(updated);
    }

    @Override
    public Date getDueDate(TaskIssue i) {
        TaskRecord r = i.getRecord();
        if (r == null || r.isFinished()) {
            return null;
        }
        return parse(record(i));
    }

    @Override
    public IssueScheduleInfo getSchedule(TaskIssue i) {
        TaskRecord r = i.getRecord();
        if (r == null || r.isFinished()) {
            return null;
        }
        Date d = parse(record(i));
        return d == null ? null : new IssueScheduleInfo(d);
    }

    private static String record(TaskIssue i) {
        return i.getRecord() == null ? null : i.getRecord().dueDate();
    }

    private static Date parse(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        String v = iso.trim();
        try {
            return Date.from(Instant.parse(v));
        } catch (DateTimeParseException e1) {
            // fall through to date-only parsing
        }
        try {
            return Date.from(LocalDate.parse(v).atStartOfDay(ZoneOffset.UTC).toInstant());
        } catch (DateTimeParseException e2) {
            return null;
        }
    }
}