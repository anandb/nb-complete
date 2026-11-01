package github.anandb.netbeans.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.h2.tools.RunScript;
import org.openide.util.NbBundle;

import github.anandb.netbeans.pattern.SqlNamePattern;
import github.anandb.netbeans.pattern.SqlSlotPattern;

/**
 * The store's SQL, kept out of the Java source: DDL in {@code schema.sql}, statements
 * in {@code queries.sql} (one {@code -- name: <key>} block each).
 *
 * <p>Blocks carry {@code ?} for bound data and {@code {{slot}}} for structure the
 * caller supplies. Slot values are code-controlled identifiers (a column name, a
 * table from {@code CAPTURE_TABLES}, a predicate) and never user input. A slot's
 * parameters are repeated at every occurrence, and {@link #render} rejects a result
 * whose placeholder count disagrees with the parameters it produced, so a predicate
 * can be used six times in one statement without a bind-count constant to keep in
 * sync.</p>
 */
final class Sql {

    /** A rendered statement and the parameters its {@code ?} placeholders expect, in order. */
    record Statement(String sql, List<Object> params) { }

    /** A slot's replacement text and the parameters it binds wherever it appears. */
    record Slot(String text, List<Object> params) {
        static Slot of(String text) {
            return new Slot(text, List.of());
        }

        static Slot of(String text, Object... params) {
            return new Slot(text, List.of(params));
        }
    }

    private static final String QUERIES = "queries.sql";
    private static final Map<String, String> BLOCKS = new LinkedHashMap<>();

    private Sql() {}

    /**
     * Runs a whole SQL script through H2's own parser. {@code schema.sql} is entirely
     * idempotent DDL, so this is safe on every open.
     */
    static void runScript(Connection conn, String script) throws SQLException {
        try (InputStream in = open(script)) {
            RunScript.execute(conn, new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SQLException(NbBundle.getMessage(Sql.class, "ERR_SqlScriptFailed", script), e);
        }
    }

    /** A statement with no slots, for callers that bind every placeholder themselves. */
    static String get(String name) {
        String sql = blocks().get(name);
        if (sql == null) {
            throw new IllegalStateException(
                    NbBundle.getMessage(Sql.class, "ERR_SqlStatementNotFound", name));
        }
        return sql;
    }

    /**
     * Expands a block's {@code {{slot}}} markers into plain SQL, for statements whose
     * {@code ?} placeholders the caller binds itself.
     */
    static String sql(String name, Map<String, Slot> slots) {
        List<Object> params = new ArrayList<>();
        String sql = expand(name, get(name), slots, params);
        if (!params.isEmpty()) {
            throw new IllegalStateException(NbBundle.getMessage(Sql.class, "ERR_SqlSlotParams", name));
        }
        return sql;
    }

    /**
     * Expands a block's {@code {{slot}}} markers. Every placeholder in the result must
     * come from a slot, so the returned parameters are exactly the ones to bind.
     */
    static Statement render(String name, Map<String, Slot> slots) {
        List<Object> params = new ArrayList<>();
        String sql = expand(name, get(name), slots, params);
        long placeholders = sql.chars().filter(c -> c == '?').count();
        if (placeholders != params.size()) {
            throw new IllegalStateException(NbBundle.getMessage(Sql.class, "ERR_SqlBindMismatch",
                    name, placeholders, params.size()));
        }
        return new Statement(sql, Collections.unmodifiableList(params));
    }

    private static String expand(String name, String template, Map<String, Slot> slots, List<Object> params) {
        Matcher m = SqlSlotPattern.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (m.find()) {
            String key = m.group(1);
            Slot slot = slots.get(key);
            if (slot == null) {
                throw new IllegalStateException(
                        NbBundle.getMessage(Sql.class, "ERR_SqlSlotUnknown", key, name));
            }
            params.addAll(slot.params());
            m.appendReplacement(out, Matcher.quoteReplacement(slot.text()));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static synchronized Map<String, String> blocks() {
        if (BLOCKS.isEmpty()) {
            BLOCKS.putAll(parse(load(QUERIES)));
        }
        return BLOCKS;
    }

    /** Splits {@code -- name: <key>} blocks; the text before the first marker is a header. */
    private static Map<String, String> parse(String script) {
        Map<String, String> blocks = new LinkedHashMap<>();
        Matcher m = SqlNamePattern.matcher(script);
        String name = null;
        int start = 0;
        while (m.find()) {
            if (name != null) {
                blocks.put(name, withoutTrailingComments(script.substring(start, m.start())));
            }
            name = m.group(1);
            start = m.end();
        }
        if (name != null) {
            blocks.put(name, withoutTrailingComments(script.substring(start)));
        }
        return blocks;
    }

    /**
     * Drops trailing comment lines: the next block's explanatory header belongs to that
     * block, but falls inside the previous block's span.
     */
    private static String withoutTrailingComments(String text) {
        String[] lines = text.split("\n", -1);
        int last = lines.length - 1;
        while (last >= 0 && (lines[last].isBlank() || lines[last].trim().startsWith("--"))) {
            last--;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i <= last; i++) {
            out.append(lines[i]).append('\n');
        }
        return out.toString().trim();
    }

    private static String load(String resource) {
        try (InputStream in = open(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    NbBundle.getMessage(Sql.class, "ERR_SqlStatementsMissing", resource), e);
        }
    }

    private static InputStream open(String resource) throws IOException {
        InputStream in = Sql.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IOException(NbBundle.getMessage(Sql.class, "ERR_SqlStatementsMissing", resource));
        }
        return in;
    }
}
