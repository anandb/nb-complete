package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.JComboBox;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.support.Logger;

import org.openide.util.NbPreferences;

// DSL-LEAF: factories are the swingtree replacement seam — createToolbarButton /
// createTextButton / createPanel / styleToolbarButton become UI.button(...) /
// UI.panel(...) literals. The 80+ call sites migrate via ComponentSpec builders in ui/spec/.
// NbPreferences preference cache (cachedUserIcon) stays imperative.
public class UIUtils {

    // --- font stacks (merged from FontStacks) --------------------------------
    // Override via system properties (aligned with existing netbeans.codingassistant.* namespace)
    private static final String FONT_OVERRIDE = System.getProperty("netbeans.codingassistant.font.family");
    private static final String MONO_OVERRIDE = System.getProperty("netbeans.codingassistant.mono.font.family");

    public static final String FONT_STACK = FONT_OVERRIDE != null
            ? "'" + FONT_OVERRIDE + "'"
            : "sans-serif";

    public static final String MONO_STACK = MONO_OVERRIDE != null
            ? "'" + MONO_OVERRIDE + "'"
            : "monospace";
    // -------------------------------------------------------------------------

    /** Cached font stack; invalidated when the LAF font family changes. */
    private static String cachedFontStack;
    private static String cachedFontFamily;

    /** Cached custom user icon — avoids disk load + scale per user bubble.
     *  Invalidated by a PreferenceChangeListener on KEY_CUSTOM_USER_ICON. */
    private static volatile String cachedUserIconPath = "";
    private static volatile Icon cachedUserIcon;

    static {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).addPreferenceChangeListener(evt -> {
            if ("customUserIcon".equals(evt.getKey())) {
                cachedUserIconPath = "";
                cachedUserIcon = null;
            }
        });
    }

    /** CSS font-family stack that respects the system-property override, then
     *  the NetBeans LAF font, then the generic fallback. */
    public static String fontStackWithActual() {
        String actual = ThemeManager.getFont().getFamily();
        if (actual == null || actual.isEmpty()) {
            return FONT_STACK;
        }
        if (actual.equals(cachedFontFamily) && cachedFontStack != null) {
            return cachedFontStack;
        }
        String quoted = actual.contains(" ") ? "'" + actual + "'" : actual;
        // System-property override > NetBeans font > generic fallback
        cachedFontFamily = actual;
        cachedFontStack = FONT_OVERRIDE != null
                ? FONT_STACK
                : quoted + ", " + FONT_STACK;
        return cachedFontStack;
    }

    private static final Logger LOG = Logger.from(UIUtils.class);

    private UIUtils() {}

    // Table separator row patterns - compiled once, avoid String.matches() per cell
    /** Standard Markdown table separator cell: optional leading colon, one or
     *  more dashes, optional trailing colon. Replaces five separate patterns. */
    private static final Pattern CELL_SEPARATOR = Pattern.compile("^:?-+:?$");
    private static final Pattern CELL_SPLIT = Pattern.compile("(?<!\\\\)\\|");

    public static JButton createToolbarButton(String iconName, String toolTip, ActionListener l) {
        return createToolbarButton(iconName, PluginSettings.getToolbarIconSize(), toolTip, l);
    }

    public static JButton createToolbarButton(String iconName, int iconSize, String toolTip, ActionListener l) {
        JButton btn = new JButton();
        btn.setIcon(ThemeManager.getIcon(iconName, iconSize));
        btn.setToolTipText(toolTip);
        btn.getAccessibleContext().setAccessibleName(toolTip);
        btn.getAccessibleContext().setAccessibleDescription(toolTip);
        styleToolbarButton(btn);
        if (l != null) {
            btn.addActionListener(l);
        }
        return btn;
    }

    public static JButton createTextButton(String text, ActionListener l) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(2, 12, 2, 12));
        btn.setPreferredSize(new Dimension(80, 32));
        btn.getAccessibleContext().setAccessibleName(text);
        btn.getAccessibleContext().setAccessibleDescription(text);
        if (l != null) {
            btn.addActionListener(l);
        }
        return btn;
    }

    public static void styleToolbarButton(JButton btn) {
        btn.putClientProperty("JButton.buttonType", "toolBarButton");
        btn.setFocusPainted(false);
        btn.setRolloverEnabled(true);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (btn.getIcon() != null) {
            int w = btn.getIcon().getIconWidth();
            int h = btn.getIcon().getIconHeight();
            btn.setPreferredSize(new Dimension(w + 8, h + 8));
        }
    }

    public static JPanel createPanel(LayoutManager layout, boolean opaque, Color bg, Border border) {
        JPanel p = new JPanel(layout);
        p.setOpaque(opaque);
        if (bg != null) {
            p.setBackground(bg);
        }
        if (border != null) {
            p.setBorder(border);
        }
        return p;
    }

    public static JPanel createTransparentPanel(LayoutManager layout) {
        return createPanel(layout, false, null, null);
    }

    public static GridBagConstraints createGbc(int x, int y, double wx, double wy, int fill, int anchor, Insets insets) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.weightx = wx;
        gbc.weighty = wy;
        gbc.fill = fill;
        gbc.anchor = anchor;
        gbc.insets = insets != null ? insets : new Insets(0, 0, 0, 0);
        return gbc;
    }

    public static Icon loadUserIcon() {
        return loadUserIcon(37);
    }

    public static Icon loadUserIcon(int size) {
        String path = PluginSettings.getCustomUserIcon();
        if (path != null && !path.isEmpty()) {
            // Return cached icon if the path hasn't changed (avoids disk load
            // + scale per user bubble on the EDT hot path).
            if (path.equals(cachedUserIconPath) && cachedUserIcon != null) {
                return cachedUserIcon;
            }
            File file = new File(path);
            if (file.exists()) {
                try {
                    ImageIcon icon = new ImageIcon(path);
                    if (icon.getIconWidth() > 0) {
                        Icon scaled = new ImageIcon(icon.getImage().getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH));
                        cachedUserIconPath = path;
                        cachedUserIcon = scaled;
                        return scaled;
                    }
                    // SVG files are not supported — log and fall through to default
                    LOG.info("Unsupported icon format (SVG) at: {0}", path);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load custom user icon", e);
                }
            }
        }
        return ThemeManager.getIcon("user.svg", size);
    }

    public static boolean isSeparatorRowLine(String line) {
        String content = line.trim();
        if (content.startsWith("|") && content.endsWith("|")) {
            if (content.length() < 2) {
                return false;
            }
            content = content.substring(1, content.length() - 1);
        }
        String[] cells = CELL_SPLIT.split(content, -1);
        List<String> rowCells = new ArrayList<>();
        for (String cell : cells) {
            rowCells.add(cell.replace("\\|", "|"));
        }
        return isSeparatorRow(rowCells);
    }

    public static boolean isSeparatorRow(List<String> row) {
        for (String cell : row) {
            if (!CELL_SEPARATOR.matcher(cell).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Installs a key listener that wraps combo box selection around:
     * up arrow at the first item goes to the last, down arrow at the last goes to the first.
     * Only activates when the popup is not visible.
     */
    /**
     * WrappingComboBox wraps selection on up/down arrows:
     * up at first item → last, down at last item → first.
     * Overrides {@code processKeyEvent} to intercept before the UI's KeyHandler fires.
     */
    public static class WrappingComboBox<E> extends JComboBox<E> {
        private static final long serialVersionUID = 1L;
        
        @Override
        public void processKeyEvent(KeyEvent e) {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                int key = e.getKeyCode();
                int idx = getSelectedIndex();
                int count = getItemCount();
                if (key == KeyEvent.VK_UP && idx <= 0 && count >= 2) {
                    setSelectedIndex(count - 1);
                    e.consume();
                    return;
                } else if (key == KeyEvent.VK_DOWN && idx >= count - 1 && count >= 2) {
                    setSelectedIndex(0);
                    e.consume();
                    return;
                }
            }
            super.processKeyEvent(e);
        }
    }

    public static Color getBubbleBackground(ColorTheme theme, String type) {
        if (null == type) {
            return theme.sunkenBackground();
        } else return switch (type) {
            case "assistant" -> theme.bubbleAssistant();
            case "user" -> theme.bubbleUser();
            case "error" -> theme.errorBackground();
            default -> theme.sunkenBackground();
        };
    }

    // --- hoisted static borders (avoid per-component allocation) -------------
    public static final Border EMPTY_BORDER = new EmptyBorder(0, 0, 0, 0);
    public static final Border CODE_WRAPPER_BORDER = new EmptyBorder(8, 20, 8, 6);
    public static final Border COPY_BUTTON_BORDER = new EmptyBorder(2, 4, 2, 4);
    public static final Border COPY_PLACEHOLDER_BORDER = new EmptyBorder(1, 2, 1, 2);
    public static final Border PERMISSION_CONTENT_BORDER = new EmptyBorder(12, 16, 12, 16);
    public static final Border PERMISSION_SMALL_BORDER = new EmptyBorder(6, 12, 6, 12);
    public static final Border SCROLL_BUTTON_BORDER = new EmptyBorder(4, 4, 4, 4);
    public static final Border AUTOCOMPLETE_BORDER = new EmptyBorder(2, 5, 2, 5);
    public static final Border PERMISSION_BUBBLE_BORDER = new EmptyBorder(8, 8, 8, 8);
    public static final Border GROUP_TOGGLE_BORDER = new EmptyBorder(0, 4, 0, 4);
    public static final Border COLLAPSIBLE_HEADER_INNER_BORDER = new EmptyBorder(5, 4, 5, 10);
    public static final Border COLLAPSIBLE_CODE_HEADER_INNER_BORDER = new EmptyBorder(6, 8, 6, 3);
    public static final Border COLLAPSIBLE_CODE_BODY_BORDER = new EmptyBorder(8, 20, 8, 6);
    public static final Border WELCOME_TITLE_BORDER = new EmptyBorder(20, 12, 10, 12);
    public static final Border WELCOME_SUBTITLE_BORDER = new EmptyBorder(0, 12, 20, 12);
    public static final Border WELCOME_BUTTON_BORDER = new EmptyBorder(12, 16, 12, 16);
    // -------------------------------------------------------------------------
}
