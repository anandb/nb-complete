package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the current XXHash64 message-id generator with the previous
 * SHA-256 implementation.
 *
 * <p>This is a coarse regression benchmark, not a microbenchmark suite. It
 * warms both paths, takes the best of five measured rounds, and requires the
 * current implementation to be faster on the same fixed payload.</p>
 */
class MessageIdGeneratorSpeedComparisonTest {

    private static final int PAYLOAD_CHARS = 4096;
    private static final int WARMUP_CALLS = 2_000;
    private static final int MEASURE_CALLS = 2_000;
    private static final int MEASURE_ROUNDS = 5;

    private interface Generator {
        String generate(String sessionId, String messageBody);
    }

    @Test
    void xxHash64IsFasterThanThePreviousSha256Generator() {
        String payload = "a".repeat(PAYLOAD_CHARS);

        warmUp(MessageIdGeneratorSpeedComparisonTest::legacySha256, payload);
        warmUp(MessageIdGenerator::generate, payload);

        long legacyNanosPerCall = bestNanosPerCall(
                MessageIdGeneratorSpeedComparisonTest::legacySha256, payload);
        long currentNanosPerCall = bestNanosPerCall(
                MessageIdGenerator::generate, payload);

        assertTrue(currentNanosPerCall < legacyNanosPerCall,
                "XXHash64 should be faster than the previous SHA-256 generator on a "
                        + PAYLOAD_CHARS + "-character payload. XXHash64: "
                        + currentNanosPerCall + " ns/call. SHA-256: "
                        + legacyNanosPerCall + " ns/call.");
    }

    private static void warmUp(Generator generator, String payload) {
        run(generator, payload, WARMUP_CALLS);
    }

    private static long bestNanosPerCall(Generator generator, String payload) {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < MEASURE_ROUNDS; round++) {
            long start = System.nanoTime();
            long sink = run(generator, payload, MEASURE_CALLS);
            long elapsed = System.nanoTime() - start;
            if (sink == Long.MIN_VALUE) {
                throw new AssertionError("Benchmark result was optimized away");
            }
            best = Math.min(best, elapsed);
        }
        return best / MEASURE_CALLS;
    }

    private static long run(Generator generator, String payload, int calls) {
        long sink = 0;
        for (int i = 0; i < calls; i++) {
            sink ^= generator.generate("benchmark-session", payload).hashCode();
        }
        return sink;
    }

    /**
     * The exact implementation that was replaced by {@link MessageIdGenerator}.
     * It is kept here only for the speed comparison.
     */
    private static String legacySha256(String sessionId, String messageBody) {
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
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
