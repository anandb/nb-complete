package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.UsageRecords.UsageSummary;
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
        String html = TokenUsageDialog.renderSummaryHtml(sample(), ThemeManager.getCurrentTheme());

        assertNotNull(html);
        assertTrue(html.contains("OVERVIEW"));
        assertTrue(html.contains("1,293"));
        assertTrue(html.contains("42,972"));
        assertTrue(html.contains("COST &amp; TOKENS"));
        assertTrue(html.contains("$137.33"));
        assertTrue(html.contains("$1.53"));
        assertTrue(html.contains("2.8M"));
        assertTrue(html.contains("77.9K"));
        assertTrue(html.contains("167.6M"));
        assertTrue(html.contains("9.7M"));
        assertTrue(html.contains("3412.6M"));
    }
}
