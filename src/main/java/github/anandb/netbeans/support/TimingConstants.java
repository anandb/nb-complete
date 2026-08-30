package github.anandb.netbeans.support;

/**
 * Shared time intervals used across the UI and manager layers.
 * Centralized to avoid magic numbers and keep timing changes in one place.
 */
public final class TimingConstants {

    private TimingConstants() {}

    /** Cooldown/flush delay for streaming content updates (milliseconds).
     *  Lower values produce smoother streaming but more EDT layout passes. */
    public static final int STREAM_FLUSH_MS = 80;

    /** Maximum characters inserted per streaming flush tick. Large deltas
     *  arriving between ticks are drip-fed across multiple ticks for a smooth
     *  typewriter effect instead of a single blocky dump. */
    public static final int STREAM_DRIP_MAX_CHARS = 120;

    /** Startup help button flash toggle interval (milliseconds). */
    public static final int HELP_FLASH_INTERVAL_MS = 700;

    /** Startup help button flash initial delay (milliseconds). */
    public static final int HELP_FLASH_INITIAL_DELAY_MS = 800;

    /** Number of flash toggle ticks for the help button. */
    public static final int HELP_FLASH_TICKS = 48;

    /** Delay before the first session is loaded at startup (milliseconds).
     *  Lets the IDE and any running installer wizard finish settling before the
     *  initial session/load is issued, so startup never races the install flow. */
    public static final int SESSION_LOAD_STARTUP_GRACE_MS = 5000;
}
