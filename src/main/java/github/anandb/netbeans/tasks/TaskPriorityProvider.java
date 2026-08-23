package github.anandb.netbeans.tasks;

import java.util.Locale;

import org.netbeans.modules.bugtracking.spi.IssuePriorityInfo;
import org.netbeans.modules.bugtracking.spi.IssuePriorityProvider;

/**
 * {@link IssuePriorityProvider} mapping the free-text {@code priority} column
 * ({@code high|normal|low}) to the Tasks Dashboard priority column. Unknown or
 * blank values fall back to {@code normal}.
 */
public final class TaskPriorityProvider implements IssuePriorityProvider<TaskIssue> {

    @Override
    public String getPriorityID(TaskIssue i) {
        String p = i.getRecord() == null ? null : i.getRecord().priority();
        if (p != null) {
            String s = p.trim().toLowerCase(Locale.ROOT);
            if (s.equals("high") || s.equals("low") || s.equals("normal")) {
                return s;
            }
        }
        return "normal";
    }

    @Override
    public IssuePriorityInfo[] getPriorityInfos() {
        return new IssuePriorityInfo[] {
            new IssuePriorityInfo("high", "High"),
            new IssuePriorityInfo("normal", "Normal"),
            new IssuePriorityInfo("low", "Low")
        };
    }
}