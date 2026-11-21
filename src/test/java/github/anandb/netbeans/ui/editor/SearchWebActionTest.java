package github.anandb.netbeans.ui.editor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Moving the browser fork off the EDT must not change what gets launched: the
 * spawn call still receives the browser executable plus the search query.
 */
class SearchWebActionTest {

    @Test
    void startFirefoxPassesTheSearchArguments() throws Exception {
        List<String> argv = new ArrayList<>();

        SearchWebAction.startFirefox("firefox", "acme corp", "https://fallback", args -> {
            argv.addAll(List.of(args));
            return null;
        });

        assertEquals(List.of("firefox", "--search", "acme corp"), argv,
                "the browser launch argv changed");
    }

    @Test
    void spawnFailureDoesNotPropagate() {
        assertDoesNotThrow(() -> SearchWebAction.startFirefox("firefox", "acme", "not a url", args -> {
            throw new IOException("firefox missing");
        }));
    }
}
