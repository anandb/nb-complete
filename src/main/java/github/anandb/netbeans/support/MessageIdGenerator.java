package github.anandb.netbeans.support;

import java.nio.charset.StandardCharsets;

import net.openhft.hashing.LongHashFunction;

/**
 * Generates deterministic message IDs for assistant messages that lack one.
 * The ID is an XXHash64 hash of (sessionId + messageBody),
 * providing a stable identifier for pinning across reloads.
 */
public final class MessageIdGenerator {

    /**
     * Pure-Java XXHash64 (no JNI, no native library extraction): the same specified
     * algorithm as before, so every stored id stays valid.
     */
    private static final LongHashFunction HASHER = LongHashFunction.xx();

    private MessageIdGenerator() {}

    /**
     * Generates an XXHash64-based message ID for an assistant message.
     * The body is stripped so live streaming text and reloaded history hash the same.
     *
     * @param sessionId       the current session ID
     * @param messageBody     the full text content of the assistant message
     * @return a hex-encoded XXHash64 hash string
     */
    public static String generate(String sessionId, String messageBody) {
        String session = sessionId == null ? "" : sessionId;
        String body = messageBody == null ? "" : messageBody.strip();
        byte[] bytes = (session + '\0' + body).getBytes(StandardCharsets.UTF_8);
        long hash = HASHER.hashBytes(bytes);
        return toHex(hash);
    }

    /** Fixed-width 16-character hex form of the 64-bit hash. */
    private static String toHex(long value) {
        String hex = Long.toHexString(value);
        return "0".repeat(16 - hex.length()) + hex;
    }
}
