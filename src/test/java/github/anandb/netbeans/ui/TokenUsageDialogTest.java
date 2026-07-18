package github.anandb.netbeans.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenUsageDialogTest {

    @BeforeAll
    static void setUp() {
        // Initialize UIManager defaults for the test environment to avoid potential NPEs
        TestUiUtils.setupTestUIManager();
    }

    @Test
    void testParseStatsRow_standardProgressRow() {
        String input = "read                      ██████████████████████████████████████ 420 (31.4%)";
        String[] parsed = TokenUsageDialog.parseStatsRow(input);
        assertEquals(3, parsed.length);
        assertEquals("read", parsed[0]);
        assertEquals("██████████████████████████████████████", parsed[1]);
        assertEquals("420 (31.4%)", parsed[2]);
    }

    @Test
    void testParseStatsRow_noSpacesBeforeBlock() {
        String input = "nb_get_opened_fi..█                                                       1 (0.1%)";
        String[] parsed = TokenUsageDialog.parseStatsRow(input);
        assertEquals(3, parsed.length);
        assertEquals("nb_get_opened_fi..", parsed[0]);
        assertEquals("█", parsed[1]);
        assertEquals("1 (0.1%)", parsed[2]);
    }

    @Test
    void testParseStatsRow_allowedCharactersInName() {
        String input = "my-tool.v2_helper█                                                       2 (0.2%)";
        String[] parsed = TokenUsageDialog.parseStatsRow(input);
        assertEquals(3, parsed.length);
        assertEquals("my-tool.v2_helper", parsed[0]);
        assertEquals("█", parsed[1]);
        assertEquals("2 (0.2%)", parsed[2]);
    }

    @Test
    void testParseStatsRow_simpleKeyValue() {
        String input = "Total Cost                $0.15";
        String[] parsed = TokenUsageDialog.parseStatsRow(input);
        assertEquals(2, parsed.length);
        assertEquals("Total Cost", parsed[0]);
        assertEquals("$0.15", parsed[1]);
    }

    @Test
    void testParseStatsRow_subHeader() {
        String input = "gemini-1.5-flash";
        String[] parsed = TokenUsageDialog.parseStatsRow(input);
        assertEquals(2, parsed.length);
        assertEquals("gemini-1.5-flash", parsed[0]);
        assertEquals("", parsed[1]);
    }

    @Test
    void testConvertStatsToHtml() {
        // Mock a box-drawing output to convert
        String rawText = 
            "┌──────────────────────────────────────────────────────────────────────────────┐\n" +
            "│ TOOL USAGE                                                                   │\n" +
            "├──────────────────────────────────────────────────────────────────────────────┤\n" +
            "│ read                      ██████████████████████████████████████ 420 (31.4%) │\n" +
            "│ nb_get_opened_fi..█                                                       1 (0.1%) │\n" +
            "│ Total Cost                $0.15                                              │\n" +
            "└──────────────────────────────────────────────────────────────────────────────┘";

        ColorTheme theme = ThemeManager.getCurrentTheme();
        String html = TokenUsageDialog.convertStatsToHtml(rawText, theme);
        
        assertNotNull(html);
        assertTrue(html.contains("colspan='3'"));
        assertTrue(html.contains("read"));
        assertTrue(html.contains("██████████████████████████████████████"));
        assertTrue(html.contains("420 (31.4%)"));
        assertTrue(html.contains("nb_get_opened_fi.."));
        assertTrue(html.contains("1 (0.1%)"));
        assertTrue(html.contains("Total Cost"));
        assertTrue(html.contains("$0.15"));
    }
}
