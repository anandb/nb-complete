package github.anandb.netbeans.manager.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.PlanEntry;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PlanStrategy implements DataExtractionStrategy {
    private static final Logger LOG = new Logger(PlanStrategy.class);

    @Override
    public boolean canHandle(SessionUpdate update) {
        return "plan".equals(update.type());
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        ProcessedMessage target = new ProcessedMessage();
        JsonNode entriesNode = update.update() != null ? update.update().entries() : null;
        if (entriesNode == null || !entriesNode.isArray()) {
            LOG.fine("Plan update has no entries array");
            return;
        }

        List<PlanEntry> entries;
        try {
            entries = MapperSupplier.get().convertValue(entriesNode, new TypeReference<List<PlanEntry>>() {});
        } catch (Exception e) {
            LOG.warn("Failed to deserialize plan entries: {0}", e.getMessage());
            return;
        }

        if (entries.isEmpty()) {
            return;
        }

        entries = new ArrayList<>(entries);
        entries.sort(Comparator.comparingInt(e -> priorityWeight(e.priority())));

        StringBuilder sb = new StringBuilder();
        sb.append("## Plan\n\n");
        for (PlanEntry entry : entries) {
            sb.append("- ").append(statusIcon(entry.status())).append(" ").append(entry.content()).append("\n");
        }

        target.setRole("assistant");
        target.setText(sb.toString());
        target.setStreaming(false);
        handler.displayMessage(target);
    }

    static int priorityWeight(String priority) {
        if (priority == null) return 3;
        return switch (priority.toLowerCase()) {
            case "high" -> 1;
            case "medium" -> 2;
            case "low" -> 3;
            default -> 3;
        };
    }

    static String statusIcon(String status) {
        if (status == null) return "\u25CB";
        return switch (status.toLowerCase()) {
            case "completed" -> "\u2705";
            case "in_progress", "in-progress", "inprogress" -> "\u25C9";
            case "pending" -> "\u23F3";
            case "cancelled", "canceled" -> "\u274C";
            case "failed" -> "\u26A0";
            default -> "\u25CB";
        };
    }
}
