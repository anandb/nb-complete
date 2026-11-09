package github.anandb.netbeans.ui.chat;

import java.util.Locale;

import java.awt.FontMetrics;

import javax.swing.Icon;
import org.openide.util.NbBundle;
import org.apache.commons.lang3.StringUtils;

import github.anandb.netbeans.pattern.ToolPrefixPattern;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.rendering.ThemeManager;

/**
 * Resolves header icons and formatted titles for collapsible panes.
 * Extracted from BaseCollapsiblePane to isolate title/icon logic.
 */
// DSL-LEAF: not a controller — builds the activity/tool header label + tag chip
// and resolves tag (read/write/edit/...) to NbBundle string. Migration target:
// CollapsibleHeaderSpec; the tag→i18n resolver stays imperative.
final class CollapsibleHeaderRenderer {

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
            case "read" -> NbBundle.getMessage(UIBundle.class, "LBL_TagRead");
            case "execute" -> NbBundle.getMessage(UIBundle.class, "LBL_TagExecute");
            case "write" -> NbBundle.getMessage(UIBundle.class, "LBL_TagWrite");
            case "edit" -> NbBundle.getMessage(UIBundle.class, "LBL_TagEdit");
            case "search" -> NbBundle.getMessage(UIBundle.class, "LBL_TagSearch");
            case "skill" -> NbBundle.getMessage(UIBundle.class, "LBL_TagSkill");
            case "context" -> NbBundle.getMessage(UIBundle.class, "LBL_TagContext");
            case "mcp" -> NbBundle.getMessage(UIBundle.class, "LBL_TagMcp");
            case "other" -> NbBundle.getMessage(UIBundle.class, "LBL_TagOther");
            case "think" -> NbBundle.getMessage(UIBundle.class, "LBL_TagThink");
            default -> null;
        };
    }

    /** Formats a raw tool title into a display-friendly string. */
    static String formatTitle(String rawTitle) {
        if (rawTitle.toUpperCase(Locale.ROOT).contains("THINKING")) {
            return NbBundle.getMessage(UIBundle.class, "LBL_ThinkingProcess");
        }

        String stripped = ToolPrefixPattern.stripPrefix(rawTitle);

        if (stripped.isEmpty() || "Tool".equalsIgnoreCase(stripped) || "Tool Call".equalsIgnoreCase(stripped)) {
            return NbBundle.getMessage(UIBundle.class, "LBL_ToolFallback");
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
        String tp = NbBundle.getMessage(UIBundle.class, "LBL_ThinkingProcess");
        return expanded ? tp : tp + "...";
    }

    /**
     * Shortens {@code text} to {@code maxWidth} pixels, keeping the end — for a path that is the
     * file name, which is what identifies the call. A directory head is trimmed first
     * ({@code /home/anand/workspace/n.../File.java}); text with no path separator is middle-elided.
     *
     * <p>Returned unchanged when it already fits, so a header never abbreviates text it has room
     * for. A null or non-positive {@code maxWidth} also returns the text whole: the caller has not
     * been laid out yet and must not guess.</p>
     */
    static String elideMiddle(String text, FontMetrics fm, int maxWidth) {
        if (text == null || text.isEmpty() || fm == null || maxWidth <= 0) {
            return text;
        }
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "\u2026";
        int ellipsisWidth = fm.stringWidth(ellipsis);

        int separator = Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'));
        String tail = separator > 0 && separator < text.length() - 1 ? text.substring(separator) : "";
        int tailWidth = tail.isEmpty() ? 0 : fm.stringWidth(tail);
        if (!tail.isEmpty() && tailWidth + ellipsisWidth < maxWidth) {
            String head = fitHead(text.substring(0, separator), fm, maxWidth - tailWidth - ellipsisWidth);
            return head + ellipsis + tail;
        }

        // No useful tail: keep both ends of the whole string.
        int half = Math.max(1, (maxWidth - ellipsisWidth) / 2);
        String start = fitHead(text, fm, half);
        String end = fitTail(text, fm, half);
        if (start.length() + end.length() >= text.length()) {
            return text;
        }
        return start + ellipsis + end;
    }

    /** Longest prefix of {@code text} that fits {@code width} pixels. */
    private static String fitHead(String text, FontMetrics fm, int width) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += fm.charWidth(text.charAt(i));
            if (total > width) {
                return text.substring(0, i);
            }
        }
        return text;
    }

    /** Longest suffix of {@code text} that fits {@code width} pixels. */
    private static String fitTail(String text, FontMetrics fm, int width) {
        int total = 0;
        for (int i = text.length() - 1; i >= 0; i--) {
            total += fm.charWidth(text.charAt(i));
            if (total > width) {
                return text.substring(i + 1);
            }
        }
        return text;
    }
}
