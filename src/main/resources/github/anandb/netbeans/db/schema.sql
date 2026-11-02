-- BeanBot usage-stats schema (H2).
--
-- Every statement is idempotent, so the script runs on every open. The
-- SCHEMA_VERSION cursor in H2UsageStatsStore only gates *data* migrations
-- (ALTERs and backfills), which stay in Java because they are conditional code.
--
-- `usage_meta` must stay first: it holds that cursor and is read immediately
-- after this script runs.

CREATE TABLE IF NOT EXISTS usage_meta (
    meta_key VARCHAR(64) PRIMARY KEY,
    meta_value VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS usage_update (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(512),
    harness_id VARCHAR(128),
    model_id VARCHAR(512),
    project VARCHAR(2048),
    captured_at BIGINT NOT NULL,
    used_tokens BIGINT NOT NULL,
    size_tokens BIGINT,
    cost_amount NUMERIC(20,6) NOT NULL,
    cost_currency VARCHAR(16)
);

CREATE TABLE IF NOT EXISTS prompt_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(512),
    harness_id VARCHAR(128),
    model_id VARCHAR(512),
    project VARCHAR(2048),
    captured_at BIGINT NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    total_tokens BIGINT NOT NULL,
    thought_tokens BIGINT,
    cached_read_tokens BIGINT
);

-- One row per goose `message_usage` custom notification: per-message tokens,
-- cache traffic, cost and timing, keyed by the harness's message id. Kept
-- separate from prompt_usage (one row per prompt result) so neither aggregate
-- double-counts the other.
CREATE TABLE IF NOT EXISTS message_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(512),
    harness_id VARCHAR(128),
    model_id VARCHAR(512),
    project VARCHAR(2048),
    captured_at BIGINT NOT NULL,
    message_id VARCHAR(512),
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    total_tokens BIGINT NOT NULL,
    cache_read_tokens BIGINT,
    cache_write_tokens BIGINT,
    cost_amount NUMERIC(20,6),
    elapsed_ms BIGINT,
    ttft_ms BIGINT,
    is_compaction BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS message_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(512),
    harness_id VARCHAR(128),
    model_id VARCHAR(512),
    project VARCHAR(2048),
    captured_at BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL
);

-- One row per finished MCP tool execution: `call_count` is 1 for every row
-- captured since schema version 8 (earlier rows hold a turn's aggregate), so a
-- SUM over the window stays correct across the upgrade. `status` is the
-- lowercase outcome (ok / error / not_found) and `duration_ms` the wall-clock
-- execution time.
CREATE TABLE IF NOT EXISTS tool_call_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(512),
    harness_id VARCHAR(128),
    model_id VARCHAR(512),
    project VARCHAR(2048),
    captured_at BIGINT NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    call_count BIGINT NOT NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ok'
);

-- Chat input history. `seq` is append order, so the newest has the highest.
CREATE TABLE IF NOT EXISTS input_history (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY,
    text CLOB NOT NULL
);

-- Per-session metadata, replacing the sessmeta_<harness> nodes and the
-- sessids_<harness> id lists. `ordinal` carries the session ordering those
-- lists encoded: within a harness the newest session has the highest ordinal.
CREATE TABLE IF NOT EXISTS session_meta (
    harness_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(512) NOT NULL,
    title CLOB,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    cwd VARCHAR(2048),
    used_tokens BIGINT,
    size_tokens BIGINT,
    ordinal BIGINT NOT NULL,
    PRIMARY KEY (harness_id, session_id)
);

-- Pinned message ids per session, replacing pinnedMessages.<sessionId>.
-- A session id is globally unique, so no harness column is needed.
CREATE TABLE IF NOT EXISTS pinned_message (
    session_id VARCHAR(512) NOT NULL,
    message_id VARCHAR(128) NOT NULL,
    pinned_at BIGINT NOT NULL,
    PRIMARY KEY (session_id, message_id)
);

-- Last known model list per harness, replacing modelList.<harnessId>.
-- `position` preserves the order the harness reported; `description` is a CLOB
-- because a model list can exceed the 8 KB preference value ceiling.
CREATE TABLE IF NOT EXISTS harness_model (
    harness_id VARCHAR(128) NOT NULL,
    position INT NOT NULL,
    model_id VARCHAR(512) NOT NULL,
    name VARCHAR(512),
    description CLOB,
    last_seen BIGINT NOT NULL,
    PRIMARY KEY (harness_id, position)
);

CREATE INDEX IF NOT EXISTS idx_usage_update_ts ON usage_update (captured_at);
CREATE INDEX IF NOT EXISTS idx_prompt_usage_ts ON prompt_usage (captured_at);
CREATE INDEX IF NOT EXISTS idx_message_event_ts ON message_event (captured_at);
CREATE INDEX IF NOT EXISTS idx_tool_call_usage_ts ON tool_call_usage (captured_at);
CREATE INDEX IF NOT EXISTS idx_tool_call_usage_tool ON tool_call_usage (tool_name);
CREATE INDEX IF NOT EXISTS idx_session_meta_harness ON session_meta (harness_id);
