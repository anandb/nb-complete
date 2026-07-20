package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PreferenceKeys;
import org.openide.util.NbPreferences;

public class MessageHistory {

    private static final Logger LOG = Logger.from(MessageHistory.class);
    private static final int MAX_SIZE = 1024;

    private final List<String> history = new ArrayList<>();
    private int index = -1;
    private String draft = "";

    public MessageHistory() {
        load();
    }

    /** Package-private constructor for unit tests — skips NbPreferences load. */
    MessageHistory(boolean loadFromPrefs) {
        if (loadFromPrefs) {
            load();
        }
    }

    public void add(String text) {
        if (text == null || text.isEmpty()) return;
        if (!history.isEmpty() && history.get(history.size() - 1).equals(text)) return;
        history.add(text);
        if (history.size() > MAX_SIZE) {
            history.remove(0);
        }
        resetNavigation();
        save();
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

    /** Returns history entries, newest first. */
    public ArrayList<String> getEntries() {
        var copy = new ArrayList<>(history);
        Collections.reverse(copy);
        return copy;
    }

    int size() {
        return history.size();
    }

    private void load() {
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        int count = prefs.getInt(PreferenceKeys.INPUT_HISTORY_COUNT, 0);
        history.clear();
        for (int i = 0; i < count; i++) {
            String entry = prefs.get(PreferenceKeys.INPUT_HISTORY_PREFIX + i, null);
            if (entry != null) {
                history.add(entry);
            }
        }
        // Trim to MAX_SIZE in case persisted config had more
        while (history.size() > MAX_SIZE) {
            history.remove(0);
        }
    }

    private void save() {
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        // Clear stale keys
        try {
            for (String key : prefs.keys()) {
                if (key.startsWith(PreferenceKeys.INPUT_HISTORY_PREFIX)) {
                    prefs.remove(key);
                }
            }
        } catch (BackingStoreException e) {
            LOG.warn("Failed to clear stale history keys: {0}", ExceptionUtils.getMessage(e));
        }
        prefs.putInt(PreferenceKeys.INPUT_HISTORY_COUNT, history.size());
        for (int i = 0; i < history.size(); i++) {
            prefs.put(PreferenceKeys.INPUT_HISTORY_PREFIX + i, history.get(i));
        }
    }
}
