package github.anandb.netbeans.model;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextReportTest {

    private static ContextReport report(long limit, long total, List<ContextReport.Segment> segments) {
        return new ContextReport("provider", "model", limit, total, segments, false, false);
    }

    @Test
    void nullSegmentsBecomeAnImmutableEmptyList() {
        ContextReport report = report(100, 10, null);

        assertTrue(report.segments().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> report.segments().add(new ContextReport.Segment("tool", "x", null, 1)));
    }

    @Test
    void segmentsAreCopiedOnConstruction() {
        List<ContextReport.Segment> segments = new ArrayList<>();
        segments.add(new ContextReport.Segment("conversation", "chat", null, 5));

        ContextReport report = report(100, 5, segments);
        segments.clear();

        assertEquals(1, report.segments().size());
        assertEquals("conversation", report.segments().get(0).category());
    }

    @Test
    void usedPercentClampsAndRounds() {
        assertEquals(0, report(0, 100, null).usedPercent());
        assertEquals(0, report(-1, 100, null).usedPercent());
        assertEquals(0, report(100, 0, null).usedPercent());
        assertEquals(25, report(200, 50, null).usedPercent());
        assertEquals(33, report(300, 100, null).usedPercent());
        assertEquals(100, report(100, 250, null).usedPercent());
    }
}
