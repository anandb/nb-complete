package github.anandb.netbeans.db;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlTest {

    /** Every block the store calls, so a rename cannot leave a statement unreachable. */
    private static final Set<String> STATEMENTS = Set.of(
            "insert_usage_update", "insert_prompt_usage", "insert_message_event",
            "insert_tool_call_usage", "merge_session_meta", "merge_pinned_message",
            "insert_harness_model", "select_session_cost", "insert_input_history",
            "prune_history_seq", "delete_input_history_before", "select_input_history",
            "count_input_history", "select_session_meta_row", "select_all_session_meta",
            "select_session_ordinals", "select_session_ordinal", "select_next_ordinal",
            "select_pins", "delete_pin", "delete_session_pins", "select_models",
            "delete_harness_models", "select_meta_value", "merge_meta_value",
            "normalize_select_model_ids", "normalize_update_model_id", "drop_capture_table",
            "alter_usage_update_cost", "add_harness_model_last_seen",
            "backfill_harness_model_last_seen", "alter_harness_model_last_seen_not_null",
            "add_tool_call_usage_duration", "add_tool_call_usage_status",
            "tool_totals", "count_scope_rows", "count_messages", "count_days",
            "sum_cost", "sum_tokens", "per_session_totals", "grouped_totals");

    @Test
    void everyStatementIsBundled() {
        for (String name : STATEMENTS) {
            assertFalse(Sql.get(name).isBlank(), name);
        }
    }

    @Test
    void blocksExcludeTheNextBlocksHeaderComment() {
        assertFalse(Sql.get("insert_prompt_usage").startsWith("--"));
        assertTrue(Sql.get("insert_prompt_usage").startsWith("INSERT INTO prompt_usage"));
    }

    @Test
    void unknownStatementFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> Sql.get("no_such_statement"));
        assertTrue(e.getMessage().contains("no_such_statement"));
    }

    @Test
    void unknownSlotFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Sql.render("count_messages", Map.of("missing", Sql.Slot.of("x"))));
        assertTrue(e.getMessage().contains("scope"));
    }

    @Test
    void scopeParametersRepeatAtEveryOccurrence() {
        Sql.Statement statement = Sql.render("count_messages",
                Map.of("scope", Sql.Slot.of("captured_at >= ? AND project = ?", 7L, "p")));

        assertEquals(2, statement.params().size());
        assertEquals(2, placeholders(statement.sql()));
    }

    /** The invariant the old {@code repetitions} constant encoded by hand. */
    @Test
    void groupedTotalsBindsOneCutoffPerScopeOccurrence() {
        Sql.Statement scoped = Sql.render("grouped_totals", Map.of(
                "scope", Sql.Slot.of("captured_at >= ? AND project = ?", 7L, "p"),
                "column", Sql.Slot.of("model_id")));
        assertEquals(placeholders(scoped.sql()), scoped.params().size());
        assertEquals(12, scoped.params().size());

        Sql.Statement all = Sql.render("grouped_totals", Map.of(
                "scope", Sql.Slot.of("captured_at >= ?", 7L),
                "column", Sql.Slot.of("model_id")));
        assertEquals(placeholders(all.sql()), all.params().size());
        assertEquals(6, all.params().size());
    }

    @Test
    void columnSlotIsSubstitutedNotBound() {
        Sql.Statement statement = Sql.render("grouped_totals", Map.of(
                "scope", Sql.Slot.of("captured_at >= ?", 7L),
                "column", Sql.Slot.of("harness_id")));

        assertTrue(statement.sql().contains("COALESCE(harness_id, '(unknown)')"));
        assertFalse(statement.sql().contains("{{"));
    }

    @Test
    void schemaScriptIsBundled() throws Exception {
        try (var in = Sql.class.getResourceAsStream("schema.sql")) {
            assertTrue(in != null, "schema.sql must be packaged next to Sql");
            String script = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String table : List.of("usage_meta", "usage_update", "prompt_usage", "message_event",
                    "tool_call_usage", "input_history", "session_meta", "pinned_message", "harness_model")) {
                assertTrue(script.contains("CREATE TABLE IF NOT EXISTS " + table), table);
            }
        }
    }

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }

    /** Guards the schema script: every {@code ;} must terminate a statement, not a comment or string. */
    @Test
    void schemaScriptHasNoStatementBodySemicolon() throws Exception {
        String script;
        try (var in = Sql.class.getResourceAsStream("schema.sql")) {
            script = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String withoutComments = script.replaceAll("(?m)^\\s*--.*$", "");
        long statements = Pattern.compile("(?m)^\\s*CREATE\\b[^;]*;")
                .matcher(withoutComments).results().count();
        assertEquals(statements, withoutComments.chars().filter(c -> c == ';').count());
    }
}
