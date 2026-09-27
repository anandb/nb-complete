package github.anandb.netbeans.support;

/**
 * Canonicalises a model id for the stats store.
 *
 * <p>Harnesses disagree on the separator between the provider prefix and the
 * model name for the very same model: {@code opencode-go:deepseek-v4.1-flash}
 * and {@code opencode-go/deepseek-v4.1-flash} are both observed, and both reach
 * the store. Stored verbatim they split one model into two groups in the
 * token-stats panel, so every attribution is canonicalised on the way in.</p>
 *
 * <p>Pure and dependency-free — safe to unit test headless.</p>
 */
public final class ModelIdNormalizer {

    private ModelIdNormalizer() {
    }

    /**
     * Returns the canonical form of a model id: trimmed, with every {@code ':'}
     * replaced by {@code '/'}. Returns {@code null} for null or blank input, so
     * an absent model stays absent rather than becoming an empty group key.
     *
     * <p>Replacing every colon, not just the first, keeps a multi-segment id
     * consistent with its slash form: {@code a:b:c} and {@code a/b/c} both
     * canonicalise to {@code a/b/c}.</p>
     */
    public static String normalize(String modelId) {
        if (modelId == null) {
            return null;
        }
        String trimmed = modelId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.indexOf(':') < 0 ? trimmed : trimmed.replace(':', '/');
    }
}