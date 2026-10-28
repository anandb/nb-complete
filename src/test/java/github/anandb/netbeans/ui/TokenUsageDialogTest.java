package github.anandb.netbeans.ui;

import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.model.UsageRecords.ToolTotals;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import java.util.List;
import javax.swing.JTextPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        String html = TokenUsageDialog.renderSummaryHtml(sample(), List.of(), List.of(), List.of(),
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

        String html = TokenUsageDialog.renderSummaryHtml(sample(), byModel, byAgent, List.of(),
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

    @Test
    void perGroupTablesFitWithinTheLegacyFixedWidth() {
        List<GroupTotals> byModel = List.of(new GroupTotals(
                "claude-sonnet-4-5-20250929", 120, 3400, 512, 88_000_000, 4_100_000, 1_200_000_000, 41.8821));
        List<GroupTotals> byAgent = List.of(new GroupTotals(
                "hermes-agent", 120, 3400, 512, 88_000_000, 4_100_000, 1_200_000_000, 41.8821));
        String html = TokenUsageDialog.renderSummaryHtml(sample(), byModel, byAgent, List.of(),
                ThemeManager.getCurrentTheme());

        JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setText(html);
        int natural = TokenUsageDialog.naturalWidth(pane);

        assertTrue(natural > 0, "the rendered content must be measurable");
        assertTrue(natural <= 700,
                "one narrow table per group must fit the legacy width, measured " + natural);
        int fitted = TokenUsageDialog.fittedWidth(natural, TokenUsageDialog.MIN_WIDTH, 10000);
        assertTrue(fitted >= natural + TokenUsageDialog.WIDTH_CHROME,
                "the fitted width must not clip the measured content, got " + fitted);
        assertTrue(fitted >= TokenUsageDialog.MIN_WIDTH, "never below the minimum width");
    }

    @Test
    void renderSummaryHtmlRendersToolHistogramAsShareBars() {
        List<ToolTotals> byTool = List.of(
                new ToolTotals("read_file", 120),
                new ToolTotals("search_project", 30),
                new ToolTotals("list_projects", 1));

        String html = TokenUsageDialog.renderSummaryHtml(sample(), List.of(), List.of(), byTool,
                ThemeManager.getCurrentTheme());

        assertNotNull(html);
        assertTrue(html.contains("MCP Tool Calls"), "the tool table must be titled");
        assertTrue(html.contains("read_file"));
        assertTrue(html.contains("120 (79%)"), "counts render with the share of all calls");
        assertTrue(html.contains("30 (20%)"), "the share follows each count");
        // The bar is a percentage-width div: a block character would print as
        // tofu in Swing's HTML kit, which has no U+2588 coverage.
        assertTrue(html.contains("width:79%;"), "the most-called tool gets the widest bar");
        assertTrue(html.contains("width:1%;"), "a rare tool keeps the 1% floor");
        assertTrue(html.contains("height:10px;"), "bars are themed 10px divs");
        assertFalse(html.contains("\u2588"), "no block characters may reach the HTML");
    }

    @Test
    void renderSummaryHtmlSeparatesTheBreakdownSections() {
        List<GroupTotals> byModel = List.of(
                new GroupTotals("gpt-x", 1, 2, 3, 4_000, 500, 600, 0.1000));
        List<GroupTotals> byAgent = List.of(
                new GroupTotals("omp", 1, 2, 3, 4_000, 500, 600, 0.1000));
        List<ToolTotals> byTool = List.of(new ToolTotals("read_file", 1));

        String html = TokenUsageDialog.renderSummaryHtml(sample(), byModel, byAgent, byTool,
                ThemeManager.getCurrentTheme());

        int summary = html.indexOf("COST &amp; TOKENS");
        int model = html.indexOf("BY MODEL");
        int agent = html.indexOf("BY AGENT");
        int tools = html.indexOf("MCP Tool Calls");
        assertTrue(summary >= 0 && summary < model && model < agent && agent < tools,
                "the sections must render in summary, model, agent, tools order");

        assertEquals(1, countSeparators(html.substring(0, model)), "a rule before BY MODEL");
        assertEquals(1, countSeparators(html.substring(model, agent)), "a rule before BY AGENT");
        assertEquals(1, countSeparators(html.substring(agent, tools)), "a rule before MCP Tool Calls");
        assertEquals(3, countSeparators(html), "no rule after the last section");
    }

    @Test
    void renderSummaryHtmlUsesTheIconTealForRulesAndBars() {
        List<GroupTotals> byModel = List.of(
                new GroupTotals("gpt-x", 1, 2, 3, 4_000, 500, 600, 0.1000));
        List<GroupTotals> byAgent = List.of(
                new GroupTotals("omp", 1, 2, 3, 4_000, 500, 600, 0.1000));
        List<ToolTotals> byTool = List.of(new ToolTotals("read_file", 1));

        String html = TokenUsageDialog.renderSummaryHtml(sample(), byModel, byAgent, byTool,
                ThemeManager.getCurrentTheme());

        boolean lightTeal = html.contains("#2A7D7D");
        boolean darkTeal = html.contains("#9FD2D2");
        assertTrue(lightTeal ^ darkTeal, "exactly one icon-teal variant must be used");
        String teal = lightTeal ? "#2A7D7D" : "#9FD2D2";
        assertEquals(3, countSeparators(html), "one rule per section");
        assertTrue(html.contains("background:" + teal + ";margin:6px 0"),
                "the rules use the icon teal");
        assertTrue(html.contains("height:10px;background:" + teal + ";"),
                "the histogram bars use the icon teal");
        assertFalse(html.contains("#589DF6") || html.contains("#268BD2"),
                "the accent blue is no longer used for the stats visuals");
    }

    @Test
    void renderSummaryHtmlOmitsSeparatorsWhenNoBreakdownExists() {
        String html = TokenUsageDialog.renderSummaryHtml(sample(), List.of(), List.of(), List.of(),
                ThemeManager.getCurrentTheme());

        assertEquals(0, countSeparators(html), "the summary alone needs no separators");
    }

    private static int countSeparators(String html) {
        String marker = "font-size:5px;line-height:5px;background:";
        int count = 0;
        for (int i = html.indexOf(marker); i >= 0; i = html.indexOf(marker, i + marker.length())) {
            count++;
        }
        return count;
    }

    @Test
    void toolPercentRoundsAndFloorsAtOne() {
        assertEquals(79, TokenUsageDialog.toolPercent(120, 151));
        assertEquals(20, TokenUsageDialog.toolPercent(30, 151));
        assertEquals(1, TokenUsageDialog.toolPercent(1, 151), "a 0.7% share rounds to the 1% floor");
        assertEquals(100, TokenUsageDialog.toolPercent(5, 5));
        assertEquals(1, TokenUsageDialog.toolPercent(0, 0), "an empty window still renders a bar");
    }

    @Test
    void renderSummaryHtmlOmitsToolTableWhenNoToolCalls() {
        String html = TokenUsageDialog.renderSummaryHtml(sample(), List.of(), List.of(), List.of(),
                ThemeManager.getCurrentTheme());

        assertFalse(html.contains("MCP Tool Calls"));
    }

    @Test
    void fittedWidthFloorsGrowsKeepsManualWidthAndClampsToScreen() {
        assertEquals(TokenUsageDialog.MIN_WIDTH, TokenUsageDialog.fittedWidth(0, 0, 1200));
        assertEquals(TokenUsageDialog.MIN_WIDTH, TokenUsageDialog.fittedWidth(0, 100, 1200));
        assertEquals(900, TokenUsageDialog.fittedWidth(400, 900, 10000));
        assertEquals(1200 + TokenUsageDialog.WIDTH_CHROME,
                TokenUsageDialog.fittedWidth(1200, TokenUsageDialog.MIN_WIDTH, 10000));
        assertTrue(TokenUsageDialog.fittedWidth(5000, TokenUsageDialog.MIN_WIDTH, 1000) <= 950,
                "a dialog wider than the screen must be clamped to it");
    }
}
