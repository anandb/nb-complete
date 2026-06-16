package github.anandb.netbeans.support;

/**
 * Shared time intervals used across the UI and manager layers.
 * Centralized to avoid magic numbers and keep timing changes in one place.
 */
public final class TimingConstants {

    private TimingConstants() {}

    /** Cooldown/flush delay for streaming content updates (milliseconds). */
    public static final int STREAM_FLUSH_MS = 300;

    /** Startup help button flash toggle interval (milliseconds). */
    public static final int HELP_FLASH_INTERVAL_MS = 700;

    /** Startup help button flash initial delay (milliseconds). */
    public static final int HELP_FLASH_INITIAL_DELAY_MS = 800;

    /** Number of flash toggle ticks for the help button. */
    public static final int HELP_FLASH_TICKS = 32;
}
