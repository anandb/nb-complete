package github.anandb.netbeans.ui;

import java.util.Locale;
import java.util.regex.Pattern;
import javax.swing.Icon;
import org.openide.util.NbBundle;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves header icons and formatted titles for collapsible panes.
 * Extracted from BaseCollapsiblePane to isolate title/icon logic.
 */
final class CollapsibleHeaderRenderer {

    private static final Pattern TOOL_PREFIX = Pattern.compile("(?i)TOOL:?\\s*");

    private CollapsibleHeaderRenderer() {
    }

    /** Returns the icon for a collapsible pane header based on its title. */
    static Icon getHeaderIcon(String title) {
        if (title.toUpperCase(Locale.ROOT).contains("THINKING")) {
            return ThemeManager.getIcon("brain.svg", 24);
        }
        return getDefaultIcon();
    }

    /** Returns the default icon for non-thinking panes. */
    static Icon getDefaultIcon() {
        return ThemeManager.getIcon("tool.svg", 24);
    }

    /** Translates a tool command tag to a localized display label. */
    static String translateTag(String tag) {
        if (tag == null) return null;
        return switch (tag.toLowerCase(Locale.ROOT)) {
            case "read" -> NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_TagRead");
            case "execute" -> NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_TagExecute");
            case "write" -> NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_TagWrite");
            case "edit" -> NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_TagEdit");
            case "search" -> NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_TagSearch");
            case "skill" -> NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_TagSkill");
            case "context" -> NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_TagContext");
            case "mcp" -> NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_TagMcp");
            case "other" -> NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_TagOther");
            case "think" -> NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_TagThink");
            default -> null;
        };
    }

    /** Formats a raw tool title into a display-friendly string. */
    static String formatTitle(String rawTitle) {
        if (rawTitle.toUpperCase(java.util.Locale.ROOT).contains("THINKING")) {
            return NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_ThinkingProcess");
        }

        String stripped = TOOL_PREFIX.matcher(rawTitle).replaceFirst("").trim();

        if (stripped.isEmpty() || "Tool".equalsIgnoreCase(stripped) || "Tool Call".equalsIgnoreCase(stripped)) {
            return NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_ToolFallback");
        }

        int pos = stripped.indexOf(' ');
        String firstWord = (pos > 1) ? stripped.substring(0, pos) : null;
        String tag = firstWord != null ? translateTag(firstWord) : null;
        String param = (pos > 1) ? stripped.substring(pos + 1) : null;

        if (tag != null && StringUtils.isNotBlank(param)) {
            return tag + " " + param;
        } else if (tag != null) {
            return tag;
        } else {
            return stripped;
        }
    }

    /** Returns the "Thinking" label for thinking panes. */
    static String thinkingLabel(boolean expanded) {
        String tp = NbBundle.getMessage(CollapsibleHeaderRenderer.class, "LBL_ThinkingProcess");
        return expanded ? tp : tp + "...";
    }
}
