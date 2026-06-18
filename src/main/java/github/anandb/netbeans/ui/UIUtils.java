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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.Border;

import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.Logger;
import org.openide.util.Utilities;

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

    /** CSS font-family stack that respects the system-property override, then
     *  the NetBeans LAF font, then the generic fallback. */
    public static String fontStackWithActual() {
        String actual = ThemeManager.getFont().getFamily();
        if (actual == null || actual.isEmpty()) {
            return FONT_STACK;
        }
        String quoted = actual.contains(" ") ? "'" + actual + "'" : actual;
        // System-property override > NetBeans font > generic fallback
        return FONT_OVERRIDE != null
                ? FONT_STACK
                : quoted + ", " + FONT_STACK;
    }

    private static final Logger LOG = Logger.from(UIUtils.class);

    private UIUtils() {}

    // Table separator row patterns - compiled once, avoid String.matches() per cell
    private static final Pattern CELL_DASH = Pattern.compile("-+");
    private static final Pattern CELL_COLON_DASH_PREFIX = Pattern.compile(":-+-.*");
    private static final Pattern CELL_DASH_COLON_SUFFIX = Pattern.compile(".*:-+");
    private static final Pattern CELL_DASH_COLON = Pattern.compile("-+:");
    private static final Pattern CELL_COLON_DASH_COLON = Pattern.compile(":.*-+");
    private static final Pattern CELL_SPLIT = Pattern.compile("(?<!\\\\)\\|");

    public static JButton createToolbarButton(String iconName, String toolTip, ActionListener l) {
        return createToolbarButton(iconName, 28, toolTip, l);
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
            File file = new File(path);
            if (file.exists()) {
                try {
                    ImageIcon icon = new ImageIcon(path);
                    if (icon.getIconWidth() > 0) {
                        return new ImageIcon(icon.getImage().getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH));
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load custom user icon", e);
                }
                return loadSvgIcon(file);
            }
        }
        return ThemeManager.getIcon("user.svg", size);
    }

    public static Icon loadSvgIcon(File file) {
        try {
            Class<?> transcoderClass = Class.forName("org.apache.batik.transcoder.image.PNGTranscoder");
            Class<?> inputClass = Class.forName("org.apache.batik.transcoder.TranscoderInput");
            Class<?> outputClass = Class.forName("org.apache.batik.transcoder.TranscoderOutput");
            Object transcoder = transcoderClass.getDeclaredConstructor().newInstance();
            transcoderClass.getMethod("addTranscodingHint", Object.class, Object.class)
                .invoke(transcoder, transcoderClass.getField("KEY_WIDTH").get(null), 37f);
            transcoderClass.getMethod("addTranscodingHint", Object.class, Object.class)
                .invoke(transcoder, transcoderClass.getField("KEY_HEIGHT").get(null), 37f);
            Object input = inputClass.getConstructor(String.class).newInstance(Utilities.toURI(file).toURL().toString());
            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            Object output = outputClass.getConstructor(OutputStream.class).newInstance(ostream);
            transcoderClass.getMethod("transcode", inputClass, outputClass).invoke(transcoder, input, output);
            return new ImageIcon(ostream.toByteArray());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load SVG custom user icon", e);
            return ThemeManager.getIcon("user.svg", 37);
        }
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
            if (!CELL_DASH.matcher(cell).matches()
                    && !CELL_COLON_DASH_PREFIX.matcher(cell).matches()
                    && !CELL_DASH_COLON_SUFFIX.matcher(cell).matches()
                    && !CELL_DASH_COLON.matcher(cell).matches()
                    && !CELL_COLON_DASH_COLON.matcher(cell).matches()) {
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
}
