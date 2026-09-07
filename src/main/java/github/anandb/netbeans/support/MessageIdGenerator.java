package github.anandb.netbeans.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates deterministic message IDs for assistant messages that lack one.
 * The ID is an SHA-256 hash of (sessionId + messageBody),
 * providing a stable identifier for pinning across reloads.
 */
public final class MessageIdGenerator {

    private MessageIdGenerator() {}

    /**
     * Generates a SHA-256 based message ID for an assistant message.
     * The body is stripped so live streaming text and reloaded history hash the same.
     *
     * @param sessionId       the current session ID
     * @param messageBody     the full text content of the assistant message
     * @return a hex-encoded SHA-256 hash string
     */
    public static String generate(String sessionId, String messageBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((sessionId != null ? sessionId : "").getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            String body = messageBody != null ? messageBody.strip() : "";
            digest.update(body.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by all Java implementations
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
