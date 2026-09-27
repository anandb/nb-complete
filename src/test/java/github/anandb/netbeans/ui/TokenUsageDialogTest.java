package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenUsageDialogTest {

    @BeforeAll
    static void setUp() {
        // Initialize UIManager defaults for the test environment to avoid potential NPEs
        TestUiUtils.setupTestUIManager();
    }

    private static UsageSummary sample() {
        return new UsageSummary(1293, 42972, 90, 137.33, 1.53, 2_800_000, 77_900,
                167_600_000L, 9_700_000L, 3_412_600_000L);
    }

    @Test
    void renderSummaryHtmlRendersBothBlocksWithFormattedFigures() {
        String html = TokenUsageDialog.renderSummaryHtml(sample(), List.of(), List.of(),
                ThemeManager.getCurrentTheme());

        assertNotNull(html);
        assertTrue(html.contains("OVERVIEW"));
        assertTrue(html.contains("1,293"));
        assertTrue(html.contains("42,972"));
        assertTrue(html.contains("COST &amp; TOKENS"));
        assertTrue(html.contains("$137.3300"));
        assertTrue(html.contains("$1.5300"));
        assertTrue(html.contains("2.8M"));
        assertTrue(html.contains("77.9K"));
        assertTrue(html.contains("167.6M"));
        assertTrue(html.contains("9.7M"));
        assertTrue(html.contains("3412.6M"));
    }

    @Test
    void renderSummaryHtmlRendersModelAndAgentGroupTables() {
        List<GroupTotals> byModel = List.of(
                new GroupTotals("gpt-x", 12, 300, 45, 8_000_000, 900_000, 1_500_000, 3.20456),
                new GroupTotals(null, 1, 7, 2, 500, 60, 10, 0.0041));
        List<GroupTotals> byAgent = List.of(
                new GroupTotals("omp", 13, 307, 47, 8_000_500, 900_060, 1_500_010, 3.20866));

        String html = TokenUsageDialog.renderSummaryHtml(sample(), byModel, byAgent,
                ThemeManager.getCurrentTheme());

        assertNotNull(html);
        assertTrue(html.contains("BY MODEL"));
        assertTrue(html.contains("BY AGENT"));
        assertTrue(html.contains("gpt-x"));
        assertTrue(html.contains("$3.2046"));
        assertTrue(html.contains("$0.0041"));
        assertTrue(html.contains("$3.2087"));
        assertTrue(html.contains("(not attributed)"));
        assertTrue(html.contains("Tool Calls"));
    }
}
