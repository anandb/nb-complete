package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.SessionControl;
import org.openide.util.Lookup;

/**
 * Resolves sub-agent title prefixes for descendant sessions.
 * Extracted from StrategyRegistry to eliminate duplicated logic.
 */
final class SubAgentTitleResolver {

    private SubAgentTitleResolver() {
    }

    /**
     * If the given sessionId belongs to a descendant (sub-agent) of the current
     * session, returns a prefixed title. Otherwise returns the original title unchanged.
     *
     * @param title          the original tool/thought title
     * @param sessionId      the session ID from the update
     * @param defaultPrefix  fallback prefix when no custom title exists (e.g. "Sub-Agent")
     * @param suffix         suffix to append (e.g. " - Thinking", " - ReadFile")
     * @return the prefixed title, or the original title if not a sub-agent
     */
    static String resolve(String title, String sessionId, String defaultPrefix, String suffix) {
        SessionControl sessionControl = Lookup.getDefault().lookup(SessionControl.class);
        if (sessionControl == null) return title;

        String currentId = sessionControl.getCurrentSessionId();
        if (sessionId == null || sessionId.equals(currentId)) return title;
        if (!sessionControl.isDescendantOfCurrent(sessionId)) return title;

        String customTitle = sessionControl.getCustomTitle(sessionId, null);
        String prefix = (customTitle != null && !customTitle.isEmpty()) ? customTitle : defaultPrefix;
        return prefix + " - " + suffix;
    }
}
