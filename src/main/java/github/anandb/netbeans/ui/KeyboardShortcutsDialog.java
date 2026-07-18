package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;

import org.openide.util.NbBundle;
import github.anandb.netbeans.support.ShortcutUtils;

/**
 * Modal dialog listing all keyboard shortcuts supported by the plugin.
 * Rendered as styled HTML for clean presentation.
 */
// DSL-LEAF: keep imperative, wrap via UI.of(...) — JDialog modal form. Low-risk DSL pilot candidate
// (self-contained; no streaming/timer bridge). Migration target: DialogSpec family.
final class KeyboardShortcutsDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static KeyboardShortcutsDialog currentInstance;

    private KeyboardShortcutsDialog(Frame owner) {
        super(owner, NbBundle.getMessage(KeyboardShortcutsDialog.class, "TITLE_KeyboardShortcuts"), false);
        setResizable(true);
        initComponents();
        pack();
        // Ensure reasonable size
        java.awt.Dimension pref = getPreferredSize();
        int w = Math.max(pref.width, 620);
        int h = Math.max(pref.height, 480);
        setPreferredSize(new java.awt.Dimension(w, h));
        setMinimumSize(new java.awt.Dimension(520, 320));
        setLocationRelativeTo(owner);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (currentInstance == KeyboardShortcutsDialog.this) {
                    currentInstance = null;
                }
            }
        });
    }

    static void show(Component parent) {
        if (currentInstance != null && currentInstance.isVisible()) {
            currentInstance.dispose();
            return;
        }
        Frame owner = parent instanceof Frame ? (Frame) parent
                : (Frame) javax.swing.SwingUtilities.getWindowAncestor(parent);
        currentInstance = new KeyboardShortcutsDialog(owner);
        currentInstance.setVisible(true);
    }

    private void initComponents() {
        JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setBorder(new EmptyBorder(12, 16, 8, 16));

        JTextPane htmlPane = new JTextPane();
        htmlPane.setEditorKit(new HTMLEditorKit());
        htmlPane.setEditable(false);
        htmlPane.setOpaque(false);
        htmlPane.setText(buildHtml());
        htmlPane.setBorder(null);

        // Make links work
        htmlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                if (e.getDescription() != null) {
                    github.anandb.netbeans.support.BrowserUtils.openOrCopyUrl(e.getDescription(), null, null);
                }
            }
        });

        // Wrap in scroll pane for long content
        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(htmlPane);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // Close hint
        JLabel closeHint = new JLabel(NbBundle.getMessage(KeyboardShortcutsDialog.class, "LBL_CloseHint"));
        closeHint.setFont(closeHint.getFont().deriveFont(Font.ITALIC, closeHint.getFont().getSize() - 1f));
        closeHint.setForeground(closeHint.getForeground().brighter());
        closeHint.setHorizontalAlignment(SwingConstants.CENTER);
        closeHint.setBorder(new EmptyBorder(4, 0, 0, 0));
        contentPanel.add(closeHint, BorderLayout.SOUTH);

        setContentPane(contentPanel);

        // Escape to close
        javax.swing.KeyStroke escapeKey = KeyStroke.getKeyStroke("ESCAPE");
        getRootPane().registerKeyboardAction(e -> dispose(), escapeKey, javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private static String buildHtml() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String border = theme.toHtmlHex(theme.tableBorder());
        String bg = theme.toHtmlHex(theme.tableBackground());
        String hdrBg = theme.toHtmlHex(theme.tableHeaderBackground());
        String altRowBg = theme.toHtmlHex(
            theme.tableBackground().brighter() instanceof java.awt.Color
                ? theme.tableBackground().brighter() : theme.tableBackground());
        String mod = System.getProperty("os.name", "").toLowerCase().contains("mac")
                ? "\u2318" : "Ctrl";
        boolean isDark = ThemeManager.getCurrentTheme().isDark();

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:sans-serif;font-size:12px;")
          .append("margin:0;padding:0;'>");

        // Primary shortcut — Toggle Assistant
        tableTwoCol(sb, border, bg, altRowBg, hdrBg, "Assistant", isDark, new String[][]{
            {mod + " + L", "Toggle assistant panel"},
        }, null);

        // Fixed shortcuts — all in one table, two per row
        tableTwoCol(sb, border, bg, altRowBg, hdrBg, "Fixed Shortcuts", isDark, new String[][]{
            {"Enter", "Send message"},
            {"Shift + Enter", "Insert newline"},
            {"/", "Slash command autocomplete"},
            {"Tab", "Switch agent / open options"},
            {"Alt + \u2191", "Previous in history"},
            {"Alt + \u2193", "Next in history"},
            {mod + " + Z", "Undo"},
            {mod + " + Y", "Redo"},
            {mod + " + R", "Search history"},
            {"PgUp", "Scroll up one page"},
            {"PgDn", "Scroll down one page"},
            {mod + " + Home", "Scroll to top"},
            {mod + " + End", "Scroll to bottom"},
        }, null);

        // Assignable shortcuts — all in one table, two per row
        String[][] assignableRows = new String[][]{
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.NewSessionAction"), "New Session"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ReloadSessionAction"), "Reload Session"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.RenameSessionAction"), "Rename Session"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ArchiveSessionAction"), "Archive Session"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.RestartServerAction"), "Restart Server"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.SendMessageAction"), "Send Message"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.StopMessageAction"), "Stop Message"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleOptionsAction"), "Toggle Options"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ExportConversationAction"), "Export Session"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleBlocksAction"), "Toggle Expand/Collapse All"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.SortLinesAction"), "Sort Lines Ascending"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.SortLinesDescAction"), "Sort Lines Descending"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.CompactJsonAction"), "Minify JSON"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.SearchWebAction"), "Search Web"},
            {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.GoToFileAction"), "Jump to file"},
        };
        tableTwoCol(sb, border, bg, altRowBg, hdrBg, "Assignable Shortcuts", isDark,
                assignableRows, "Assign via Tools > Keymap");

        // Stash diff shortcuts
        tableTwoCol(sb, border, bg, altRowBg, hdrBg, "Stash Diff (Experimental)", isDark, new String[][]{
            {mod + " + Shift + L", "Open stash diff viewer"},
            {mod + " + ,", "Previous difference"},
            {mod + " + .", "Next difference"},
        }, null);

        sb.append("</body></html>");
        return sb.toString();
    }

    private static void tableTwoCol(StringBuilder sb, String border, String bg,
            String altRowBg, String hdrBg, String title, boolean isDark,
            String[][] rows, String footnote) {
        String tdStyle = "padding:7px 10px;border:1px solid " + border;
        String keyBadge = tdStyle + ";font-family:monospace;font-size:11px;font-weight:600;"
                + "background:" + (isDark ? "#2a2d35" : "#e8e8ec") + ";"
                + "border-radius:4px;text-align:center;white-space:nowrap;";
        String noneStyle = tdStyle + ";color:" + (isDark ? "#666" : "#999") + ";font-style:italic;";
        String hdrTh = " align='left' bgcolor='" + hdrBg
                + "' style='padding:7px 10px;border:1px solid " + border
                + ";font-size:11px;text-transform:uppercase;letter-spacing:0.5px;'";
        String sectionDivider = "border-top:2px solid " + border + ";margin-top:16px;";

        sb.append("<div style='").append(sectionDivider).append("'>");
        sb.append("<p style='font-weight:bold;font-size:13px;margin:8px 0 6px;color:")
          .append(isDark ? "#e0e0e0" : "#333").append(";'>")
          .append(title).append("</p>");

        if (footnote != null) {
            sb.append("<p style='font-size:10px;margin:0 0 6px;color:")
              .append(isDark ? "#888" : "#666").append(";'>")
              .append(footnote).append("</p>");
        }

        sb.append("<table border='1' bordercolor='").append(border)
          .append("' cellspacing='0' cellpadding='0'")
          .append(" style='border-collapse:collapse;width:100%;")
          .append("background:").append(bg).append(";border-radius:6px;overflow:hidden;'>");
        sb.append("<tr>")
          .append("<th").append(hdrTh).append(">Key</th>")
          .append("<th").append(hdrTh).append(">Action</th>")
          .append("<th").append(hdrTh).append(">Key</th>")
          .append("<th").append(hdrTh).append(">Action</th>")
          .append("</tr>");

        int len = rows.length;
        for (int i = 0; i < len; i += 2) {
            String rowBg = (i / 2 % 2 == 0) ? bg : altRowBg;
            String[] left = rows[i];
            String[] right = (i + 1 < len) ? rows[i + 1] : new String[]{"", ""};

            sb.append("<tr style='background:").append(rowBg).append(";'>");
            sb.append("<td style='").append(keyBadge).append(";'>")
              .append(wrapKey(left[0])).append("</td>");
            sb.append("<td style='").append(tdStyle).append(";'>").append(left[1]).append("</td>");
            sb.append("<td style='").append(right[0].isEmpty() ? tdStyle : keyBadge)
              .append(";'>").append(wrapKey(right[0])).append("</td>");
            sb.append("<td style='").append(tdStyle).append(";'>").append(right[1]).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table></div>");
    }

    /** Wrap key text in a styled badge span; blank for empty keys. */
    private static String wrapKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        // "None" entries get special styling
        if ("None".equals(key)) {
            return key;
        }
        return key;
    }
}
