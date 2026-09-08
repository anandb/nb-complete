package github.anandb.netbeans.model;

import java.util.Map;

/**
 * Maps Claude mode IDs to agent permission profiles.
 * Modes control the permission/behavior level of the agent.
 */
public final class ModeAgentMapping {

    /** Permission profile for a mode. */
    public record PermissionProfile(
        boolean autoAcceptEdits,
        boolean skipPermissionPrompts,
        boolean planModeOnly,
        String description
    ) {}

    private static final Map<String, PermissionProfile> MODE_PROFILES = Map.of(
        "default", new PermissionProfile(
                false, false, false,
                "Standard behavior, prompts for dangerous operations"),
        "acceptEdits", new PermissionProfile(
                true, false, false,
                "Auto-accept file edit operations"),
        "plan", new PermissionProfile(
                false, false, true,
                "Planning mode, no actual tool execution"),
        "dontAsk", new PermissionProfile(
                false, true, false,
                "Don't prompt for permissions, deny if not pre-approved"),
        "bypassPermissions", new PermissionProfile(
                true, true, false,
                "Bypass all permission checks")
    );

    private static final PermissionProfile DEFAULT_PROFILE = MODE_PROFILES.get("default");

    private ModeAgentMapping() {}

    /**
     * Returns the permission profile for the given mode ID.
     * Falls back to the default profile if the mode is unknown.
     */
    public static PermissionProfile getProfile(String modeId) {
        if (modeId == null) {
            return DEFAULT_PROFILE;
        }
        return MODE_PROFILES.getOrDefault(modeId, DEFAULT_PROFILE);
    }

    /**
     * Returns true if the mode ID is a valid known mode.
     */
    public static boolean isValidMode(String modeId) {
        return modeId != null && MODE_PROFILES.containsKey(modeId);
    }
}
