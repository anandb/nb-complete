-- BeanBot usage-stats statements (H2).
--
-- One block per statement, introduced by a `-- name: <key>` line. Blocks carry
-- `?` for bound data and `{{slot}}` for structure that the caller supplies
-- (a column name, a table from CAPTURE_TABLES, a predicate). Slot values are
-- code-controlled identifiers only — never user input.
--
-- Sql.render() duplicates a slot's parameters at every occurrence and rejects a
-- render whose placeholder count does not match the parameters it produced, so
-- a scope predicate can be repeated any number of times without a bind-count
-- constant to keep in sync.

-- name: insert_usage_update
INSERT INTO usage_update
    (session_id, harness_id, model_id, project, captured_at, used_tokens, size_tokens,
     cost_amount, cost_currency)
VALUES (?,?,?,?,?,?,?,?,?)

-- name: insert_prompt_usage
INSERT INTO prompt_usage
    (session_id, harness_id, model_id, project, captured_at, input_tokens, output_tokens,
     total_tokens, thought_tokens, cached_read_tokens)
VALUES (?,?,?,?,?,?,?,?,?,?)

-- name: insert_message_event
INSERT INTO message_event
    (session_id, harness_id, model_id, project, captured_at, kind)
VALUES (?,?,?,?,?,?)

-- name: insert_message_usage
INSERT INTO message_usage
    (session_id, harness_id, model_id, project, captured_at, message_id, input_tokens,
     output_tokens, total_tokens, cache_read_tokens, cache_write_tokens, cost_amount,
     elapsed_ms, ttft_ms, is_compaction)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)

-- name: insert_tool_call_usage
INSERT INTO tool_call_usage
    (session_id, harness_id, model_id, project, captured_at, tool_name, call_count,
     duration_ms, status)
VALUES (?,?,?,?,?,?,?,?,?)

-- name: merge_session_meta
MERGE INTO session_meta
    (harness_id, session_id, title, hidden, cwd, used_tokens, size_tokens, ordinal)
VALUES (?,?,?,?,?,?,?,?)

-- name: merge_pinned_message
MERGE INTO pinned_message (session_id, message_id, pinned_at) VALUES (?,?,?)

-- name: insert_harness_model
INSERT INTO harness_model (harness_id, position, model_id, name, description, last_seen)
VALUES (?,?,?,?,?,?)

-- name: select_session_cost
SELECT COALESCE(SUM(cost_amount), 0) FROM usage_update WHERE session_id = ?

-- name: insert_input_history
INSERT INTO input_history (text) VALUES (?)

-- name: prune_history_seq
SELECT seq FROM input_history ORDER BY seq DESC LIMIT 1 OFFSET ?

-- name: delete_input_history_before
DELETE FROM input_history WHERE seq < ?

-- name: select_input_history
SELECT text FROM input_history ORDER BY seq

-- name: count_input_history
SELECT COUNT(*) FROM input_history

-- name: select_session_meta_row
SELECT title, hidden, cwd, used_tokens, size_tokens FROM session_meta
 WHERE harness_id = ? AND session_id = ?

-- name: select_all_session_meta
SELECT harness_id, session_id, title, hidden, cwd, used_tokens, size_tokens
 FROM session_meta ORDER BY harness_id, ordinal

-- name: select_session_ordinals
SELECT session_id, ordinal FROM session_meta WHERE harness_id = ?

-- name: select_session_ordinal
SELECT ordinal FROM session_meta WHERE harness_id = ? AND session_id = ?

-- name: select_next_ordinal
SELECT COALESCE(MAX(ordinal), 0) + 1 FROM session_meta WHERE harness_id = ?

-- name: select_pins
SELECT session_id, message_id FROM pinned_message

-- name: delete_pin
DELETE FROM pinned_message WHERE session_id = ? AND message_id = ?

-- name: delete_session_pins
DELETE FROM pinned_message WHERE session_id = ?

-- name: select_models
SELECT harness_id, model_id, name, description, last_seen FROM harness_model
 ORDER BY harness_id, position

-- name: delete_harness_models
DELETE FROM harness_model WHERE harness_id = ?

-- name: select_meta_value
SELECT meta_value FROM usage_meta WHERE meta_key = ?

-- name: merge_meta_value
MERGE INTO usage_meta (meta_key, meta_value) VALUES (?, ?)

-- name: normalize_select_model_ids
SELECT DISTINCT model_id FROM {{table}} WHERE model_id LIKE '%:%'

-- name: normalize_update_model_id
UPDATE {{table}} SET model_id = ? WHERE model_id = ?

-- name: drop_capture_table
DROP TABLE IF EXISTS {{table}}

-- name: alter_usage_update_cost
ALTER TABLE usage_update ALTER COLUMN cost_amount NUMERIC(20,6)

-- name: add_harness_model_last_seen
ALTER TABLE IF EXISTS harness_model ADD COLUMN IF NOT EXISTS last_seen BIGINT

-- name: backfill_harness_model_last_seen
UPDATE harness_model SET last_seen = ? WHERE last_seen IS NULL

-- name: alter_harness_model_last_seen_not_null
ALTER TABLE IF EXISTS harness_model ALTER COLUMN last_seen SET NOT NULL

-- name: add_tool_call_usage_duration
ALTER TABLE IF EXISTS tool_call_usage ADD COLUMN IF NOT EXISTS duration_ms BIGINT DEFAULT 0 NOT NULL

-- name: add_tool_call_usage_status
ALTER TABLE IF EXISTS tool_call_usage ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'ok' NOT NULL

-- Per-tool totals over the window, most-called first: one row per tool_name, the
-- sum of each execution's call_count, plus how many of them did not end OK.
-- name: tool_totals
SELECT tool_name,
       COALESCE(SUM(call_count), 0) AS calls,
       COALESCE(SUM(CASE WHEN status <> 'ok' THEN call_count ELSE 0 END), 0) AS failures
 FROM tool_call_usage
 WHERE {{scope}}
 GROUP BY tool_name ORDER BY calls DESC, tool_name ASC

-- name: count_scope_rows
SELECT COUNT(*) FROM (
    SELECT {{column}} FROM usage_update WHERE {{scope}}
    UNION SELECT {{column}} FROM prompt_usage WHERE {{scope}}
    UNION SELECT {{column}} FROM message_event WHERE {{scope}}
) scope_rows

-- name: count_messages
SELECT COUNT(*) FROM message_event WHERE {{scope}}

-- name: count_days
SELECT COUNT(*) FROM (
    SELECT DISTINCT {{dayExpr}} FROM usage_update WHERE {{scope}}
    UNION SELECT DISTINCT {{dayExpr}} FROM prompt_usage WHERE {{scope}}
    UNION SELECT DISTINCT {{dayExpr}} FROM message_event WHERE {{scope}}
) day_rows

-- name: sum_cost
SELECT COALESCE(SUM(cost_amount), 0) FROM usage_update WHERE {{scope}}

-- name: sum_tokens
SELECT COALESCE(SUM(input_tokens), 0), COALESCE(SUM(output_tokens), 0),
       COALESCE(SUM(cached_read_tokens), 0) FROM prompt_usage WHERE {{scope}}

-- Per-session token sums for the Avg/Median Tokens/Session roll-up. The four
-- stored token fields are summed here and only here; the reported Input,
-- Output and Cache Read columns each stay a single-field sum.
-- name: per_session_totals
SELECT SUM(COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0)
           + COALESCE(thought_tokens, 0) + COALESCE(cached_read_tokens, 0))
  FROM prompt_usage WHERE {{scope}} GROUP BY session_id

-- Grouped aggregation across the three tables, keyed to the distinct set of
-- group values seen in the window. Each table contributes only the figures it
-- owns (prompt_usage: token columns; usage_update: cost deltas; message_event:
-- messages, tool calls and the session count). The group key source is a single
-- UNION of the three attribution columns, so a group key appears once even when
-- several tables carry it.
--
-- One COALESCE-populated group set, then three LEFT JOINed aggregates. A single
-- source of group keys avoids the double session counting a UNION of per-branch
-- COUNT(DISTINCT session_id) produces (the same session appears in two branches
-- when both recorded rows).
-- name: grouped_totals
SELECT g.grp,
       COALESCE(a.sessions, 0) AS sessions,
       COALESCE(a.messages, 0) AS messages,
       COALESCE(a.tool_calls, 0) AS tool_calls,
       COALESCE(t.input_tokens, 0) AS input_tokens,
       COALESCE(t.output_tokens, 0) AS output_tokens,
       COALESCE(t.cached_read_tokens, 0) AS cached_read_tokens,
       COALESCE(c.cost, 0.0) AS cost
  FROM (
        SELECT DISTINCT COALESCE(ids, '(unknown)') AS grp FROM (
            SELECT {{column}} AS ids FROM prompt_usage WHERE {{scope}}
            UNION SELECT {{column}} FROM usage_update WHERE {{scope}}
            UNION SELECT {{column}} FROM message_event WHERE {{scope}}
        )
  ) g
  LEFT JOIN (
        SELECT COALESCE({{column}}, '(unknown)') AS grp,
               COALESCE(SUM(input_tokens), 0) AS input_tokens,
               COALESCE(SUM(output_tokens), 0) AS output_tokens,
               COALESCE(SUM(cached_read_tokens), 0) AS cached_read_tokens
          FROM prompt_usage WHERE {{scope}}
         GROUP BY COALESCE({{column}}, '(unknown)')
  ) t ON t.grp = g.grp
  LEFT JOIN (
        SELECT COALESCE({{column}}, '(unknown)') AS grp,
               COALESCE(SUM(cost_amount), 0.0) AS cost
          FROM usage_update WHERE {{scope}}
         GROUP BY COALESCE({{column}}, '(unknown)')
  ) c ON c.grp = g.grp
  LEFT JOIN (
        SELECT COALESCE({{column}}, '(unknown)') AS grp,
               COUNT(DISTINCT CASE WHEN kind IN ('USER', 'ASSISTANT', 'THOUGHT')
                                   THEN session_id END) AS sessions,
               SUM(CASE WHEN kind <> 'TOOL' THEN 1 ELSE 0 END) AS messages,
               SUM(CASE WHEN kind = 'TOOL' THEN 1 ELSE 0 END) AS tool_calls
          FROM message_event WHERE {{scope}}
         GROUP BY COALESCE({{column}}, '(unknown)')
  ) a ON a.grp = g.grp
 ORDER BY g.grp
