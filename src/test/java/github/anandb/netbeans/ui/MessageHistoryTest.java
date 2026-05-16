package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageHistoryTest {

    @Test
    void addStoresMessage() {
        MessageHistory mh = new MessageHistory();
        mh.add("hello");
        assertFalse(mh.isEmpty());
    }

    @Test
    void navigateUpSavesDraftAndReturnsLastMessage() {
        MessageHistory mh = new MessageHistory();
        mh.add("first");
        mh.add("second");
        String result = mh.navigateUp("current draft");
        assertEquals("second", result);
        assertTrue(mh.isNavigating());
    }

    @Test
    void navigateDownReturnsToDraft() {
        MessageHistory mh = new MessageHistory();
        mh.add("first");
        mh.navigateUp("draft");
        String result = mh.navigateDown("first");
        assertEquals("draft", result);
        assertFalse(mh.isNavigating());
    }

    @Test
    void navigateDownWithinHistoryMovesForward() {
        MessageHistory mh = new MessageHistory();
        mh.add("first");
        mh.add("second");
        mh.add("third");
        mh.navigateUp("draft");        // at "third"
        String r1 = mh.navigateUp("draft");  // at "second" (index 1)
        assertEquals("second", r1);
        // navigateUp again goes to "first" (index 0)
        String r2 = mh.navigateUp("draft");
        assertEquals("first", r2);
        // navigateDown from "first" goes to "second" (index 1)
        String r3 = mh.navigateDown("first");
        assertEquals("second", r3);
        assertTrue(mh.isNavigating());
    }

    @Test
    void navigateUpOnEmptyReturnsSameText() {
        MessageHistory mh = new MessageHistory();
        String result = mh.navigateUp("typing");
        assertEquals("typing", result);
        assertFalse(mh.isNavigating());
    }

    @Test
    void addSkipsDuplicateConsecutive() {
        MessageHistory mh = new MessageHistory();
        mh.add("msg");
        mh.add("msg");
        assertEquals(1, mh.size());
    }

    @Test
    void addLimitsTo50() {
        MessageHistory mh = new MessageHistory();
        for (int i = 0; i < 60; i++) {
            mh.add("msg" + i);
        }
        String r = mh.navigateUp("draft");
        assertEquals("msg59", r);
    }

    @Test
    void resetNavigationClearsState() {
        MessageHistory mh = new MessageHistory();
        mh.add("msg");
        mh.navigateUp("draft");
        assertTrue(mh.isNavigating());
        mh.resetNavigation();
        assertFalse(mh.isNavigating());
    }

    @Test
    void addDoesNotStoreNull() {
        MessageHistory mh = new MessageHistory();
        mh.add(null);
        assertTrue(mh.isEmpty());
    }

    @Test
    void addDoesNotStoreEmpty() {
        MessageHistory mh = new MessageHistory();
        mh.add("");
        assertTrue(mh.isEmpty());
    }
}
