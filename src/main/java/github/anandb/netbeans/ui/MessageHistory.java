package github.anandb.netbeans.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import github.anandb.netbeans.contract.SessionStore;
import org.openide.util.Lookup;

/**
 * Up/down history for the chat input.
 *
 * <p>The {@code SessionStore} owns the entries and their persistence, so this
 * holds only the navigation cursor. Entries are read from the store on each use
 * rather than captured once: the store loads on its own thread, so a snapshot
 * taken at construction — or at first use — could latch an empty list for the
 * life of the component. Reading per use costs nothing, because the store
 * answers from memory.</p>
 */
public class MessageHistory {

    private static final int MAX_SIZE = 1024;

    private final SessionStore store;
    /** Used only when no store is registered, which is the unit-test case. */
    private final List<String> local = new ArrayList<>();
    private int index = -1;
    private String draft = "";

    /** Production constructor: reads through the registered store. */
    public MessageHistory() {
        this(Lookup.getDefault().lookup(SessionStore.class));
    }

    /** Constructor for unit tests: {@code false} keeps the history purely in memory. */
    MessageHistory(boolean useStore) {
        this(useStore ? Lookup.getDefault().lookup(SessionStore.class) : null);
    }

    /** Explicit store, for the tests; production resolves it from the registry. */
    MessageHistory(SessionStore store) {
        this.store = store;
    }

    private List<String> entries() {
        return store != null ? store.inputHistory() : local;
    }

    public void add(String text) {
        if (text == null || text.isEmpty()) return;
        List<String> entries = entries();
        if (!entries.isEmpty() && entries.get(entries.size() - 1).equals(text)) return;
        if (store != null) {
            store.appendInputHistory(text);
        } else {
            local.add(text);
            while (local.size() > MAX_SIZE) {
                local.remove(0);
            }
        }
        resetNavigation();
    }

    public String navigateUp(String currentInputText) {
        List<String> entries = entries();
        if (entries.isEmpty()) return currentInputText;
        if (index == -1) {
            draft = currentInputText;
            index = entries.size() - 1;
        } else if (index > 0) {
            index--;
        }
        return entries.get(Math.min(index, entries.size() - 1));
    }

    public String navigateDown(String currentInputText) {
        List<String> entries = entries();
        if (entries.isEmpty() || index == -1) return currentInputText;
        if (index < entries.size() - 1) {
            index++;
            return entries.get(index);
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
        return entries().isEmpty();
    }

    /** Returns history entries, newest first. */
    public ArrayList<String> getEntries() {
        var copy = new ArrayList<>(entries());
        Collections.reverse(copy);
        return copy;
    }

    int size() {
        return entries().size();
    }
}