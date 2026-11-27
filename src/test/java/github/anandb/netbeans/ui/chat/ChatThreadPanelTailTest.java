package github.anandb.netbeans.ui.chat;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The max-bubbles cap counts user/assistant rows only. A tool-heavy transcript
 * previously consumed the whole window with activity rows, leaving a handful of
 * visible conversation bubbles.
 */
class ChatThreadPanelTailTest {

    private record Row(String role) { }

    private static List<Row> rows(String... roles) {
        return java.util.Arrays.stream(roles).map(Row::new).toList();
    }

    private static java.util.function.Function<Row, String> role() {
        return Row::role;
    }

    @Test
    void capCountsConversationRowsOnly() {
        // tool, user, tool, assistant, thought, tool, user, assistant, tool
        List<Row> items = rows("tool", "user", "tool", "assistant", "thought",
                "tool", "user", "assistant", "tool");

        assertEquals(6, ChatThreadPanel.tailStartForMax(items, role(), 2),
                "the last two user/assistant rows start at the user row");
        assertEquals(7, ChatThreadPanel.tailStartForMax(items, role(), 1));
    }

    @Test
    void capNeverCutsInsideTheRetainedTail() {
        List<Row> items = rows("tool", "thought", "user", "tool", "assistant", "tool");
        int start = ChatThreadPanel.tailStartForMax(items, role(), 2);

        List<Row> kept = items.subList(start, items.size());
        assertEquals(2, kept.stream()
                .filter(r -> "user".equals(r.role()) || "assistant".equals(r.role()))
                .count());
        assertEquals("tool", kept.get(kept.size() - 1).role(), "trailing activity stays");
    }

    @Test
    void fewerConversationRowsThanCapKeepsEverything() {
        List<Row> items = rows("tool", "user", "thought", "assistant", "tool");

        assertEquals(0, ChatThreadPanel.tailStartForMax(items, role(), 100));
        assertEquals(0, ChatThreadPanel.tailStartForMax(items, role(), 0));
    }
}
