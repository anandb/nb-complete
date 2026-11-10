package github.anandb.netbeans.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the agent modes reported by the server in a session handshake (the same
 * message that carries Claude's models). Fully populated from the incoming
 * {@link ModesInfo}; nothing is hardcoded.
 */
public final class ModeAgentMapping {

    private static volatile Map<String, AvailableMode> liveModes = Collections.emptyMap();

    private ModeAgentMapping() {}

    /**
     * Populates the mapping from the modes reported in an incoming session
     * message. Every mode is taken from the server response; no defaults or
     * built-in profiles are assumed.
     */
    public static void populate(ModesInfo modes) {
        if (modes == null || modes.availableModes() == null) {
            liveModes = Collections.emptyMap();
            return;
        }
        Map<String, AvailableMode> byId = new LinkedHashMap<>();
        for (AvailableMode m : modes.availableModes()) {
            if (m.id() != null) {
                byId.put(m.id(), m);
            }
        }
        liveModes = byId;
    }

    /** All modes advertised by the server in the latest handshake. */
    public static List<AvailableMode> getAvailableModes() {
        return List.copyOf(liveModes.values());
    }

}
