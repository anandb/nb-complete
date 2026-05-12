package github.anandb.netbeans.ui;

import java.util.ArrayList;
import java.util.List;

public class MessageHistory {

    private static final int MAX_SIZE = 50;

    private final List<String> history = new ArrayList<>();
    private int index = -1;
    private String draft = "";

    public void add(String text) {
        if (text == null || text.isEmpty()) return;
        if (!history.isEmpty() && history.get(history.size() - 1).equals(text)) return;
        history.add(text);
        if (history.size() > MAX_SIZE) {
            history.remove(0);
        }
        resetNavigation();
    }

    public String navigateUp(String currentInputText) {
        if (history.isEmpty()) return currentInputText;
        if (index == -1) {
            draft = currentInputText;
            index = history.size() - 1;
        } else if (index > 0) {
            index--;
        }
        return history.get(index);
    }

    public String navigateDown(String currentInputText) {
        if (history.isEmpty() || index == -1) return currentInputText;
        if (index < history.size() - 1) {
            index++;
            return history.get(index);
        } else {
            String savedDraft = draft;
            resetNavigation();
            return savedDraft;
        }
    }

    public void resetNavigation() {
        index = -1;
        draft = "";
    }

    public boolean isNavigating() {
        return index != -1;
    }

    public boolean isEmpty() {
        return history.isEmpty();
    }

    int size() {
        return history.size();
    }
}
