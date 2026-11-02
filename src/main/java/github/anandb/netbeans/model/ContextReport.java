package github.anandb.netbeans.model;

import java.util.List;

/**
 * What fills a session's context window right now, from goose's
 * {@code _goose/unstable/context/report}.
 *
 * <p>The report makes no model call: goose measures the conversation it would send with its
 * local tokenizer. Segments are non-overlapping slices (base prompt, each tool definition,
 * grouped conversation parts) that add up to {@link #totalTokens()}.</p>
 *
 * @param provider              provider id, e.g. {@code openrouter}; null when the harness omits it
 * @param modelName             the model the window belongs to
 * @param contextLimit          the model's context window in tokens; 0 when unknown
 * @param totalTokens           measured tokens of the next request
 * @param segments              non-overlapping slices, in request order then by message kind
 * @param providerManagesContext the provider keeps its own history and only receives the latest
 *                               prompt, so there is nothing to break down
 * @param autoCompactionPending the session is past the compaction threshold and the next
 *                              request compacts the conversation first
 */
public record ContextReport(
        String provider,
        String modelName,
        long contextLimit,
        long totalTokens,
        List<Segment> segments,
        boolean providerManagesContext,
        boolean autoCompactionPending) {

    public ContextReport {
        segments = segments != null ? List.copyOf(segments) : List.of();
    }

    /**
     * One slice of the window. {@code category} is goose's snake_case kind
     * ({@code system_prompt}, {@code tool}, {@code conversation}, …); {@code source} is
     * display-only provenance such as a file path.
     */
    public record Segment(String category, String label, String source, long tokenCount) {
    }

    /** Share of the context limit the report fills, clamped to 0..100; 0 when the limit is unknown. */
    public int usedPercent() {
        if (contextLimit <= 0 || totalTokens <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, Math.round(100.0 * totalTokens / contextLimit)));
    }
}
