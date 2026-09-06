package github.anandb.netbeans.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Generates deterministic message IDs for assistant messages that lack one.
 * The ID is an SHA-256 hash of (sessionId + messageBody + userMessageIndex),
 * providing a stable identifier for pinning across reloads.
 */
public final class MessageIdGenerator {

    private MessageIdGenerator() {}

    /**
     * Generates a SHA-256 based message ID for an assistant message.
     *
     * @param sessionId       the current session ID
     * @param messageBody     the full text content of the assistant message
     * @param userMessageIndex the 0-based index of the last user message in this session
     * @return a hex-encoded SHA-256 hash string
     */
    public static String generate(String sessionId, String messageBody, int userMessageIndex) {
        String input = (sessionId != null ? sessionId : "")
                + (messageBody != null ? messageBody : "")
                + userMessageIndex;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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
