package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;

import org.openide.util.NbBundle;
import github.anandb.netbeans.support.BrowserUtils;
import github.anandb.netbeans.support.ShortcutUtils;

/**
 * Modal dialog listing all keyboard shortcuts supported by the plugin.
 * Rendered as styled HTML for clean presentation. Includes live search filtering.
 */
// DSL-LEAF: keep imperative, wrap via UI.of(...) — JDialog modal form. Low-risk DSL pilot candidate
// (self-contained; no streaming/timer bridge). Migration target: DialogSpec family.
final class KeyboardShortcutsDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static KeyboardShortcutsDialog currentInstance;

    private JTextField searchField;
    private JTextPane htmlPane;
    private ShortcutSection[] sections;

    private KeyboardShortcutsDialog(Frame owner) {
        super(owner, NbBundle.getMessage(KeyboardShortcutsDialog.class, "TITLE_KeyboardShortcuts"), false);
        setResizable(true);
        initComponents();
        pack();
        Dimension pref = getPreferredSize();
        int w = Math.max(pref.width, 640);
        int h = Math.max(pref.height, 520);
        setPreferredSize(new Dimension(w, h));
        setMinimumSize(new Dimension(520, 360));
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
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
                : (Frame) SwingUtilities.getWindowAncestor(parent);
        currentInstance = new KeyboardShortcutsDialog(owner);
        currentInstance.setVisible(true);
    }

    private void initComponents() {
        sections = buildSections();

        JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setBorder(new EmptyBorder(12, 16, 8, 16));

        // Search bar
        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel(NbBundle.getMessage(KeyboardShortcutsDialog.class, "TITLE_KeyboardShortcuts"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize() + 2f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        searchField = new JTextField(16);
        searchField.putClientProperty("JTextField.placeholderText",
                NbBundle.getMessage(KeyboardShortcutsDialog.class, "LBL_FilterShortcuts"));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { refreshHtml(); }
            @Override
            public void removeUpdate(DocumentEvent e) { refreshHtml(); }
            @Override
            public void changedUpdate(DocumentEvent e) { refreshHtml(); }
        });
        headerPanel.add(searchField, BorderLayout.EAST);
        contentPanel.add(headerPanel, BorderLayout.NORTH);

        // HTML pane
        htmlPane = new JTextPane();
        htmlPane.setEditorKit(new HTMLEditorKit());
        htmlPane.setEditable(false);
        htmlPane.setOpaque(false);
        htmlPane.setBorder(null);
        refreshHtml();

        htmlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getDescription() != null) {
                BrowserUtils.openOrCopyUrl(e.getDescription(), null, null);
            }
        });

        JScrollPane scrollPane = new JScrollPane(htmlPane);
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

        KeyStroke escapeKey = KeyStroke.getKeyStroke("ESCAPE");
        getRootPane().registerKeyboardAction(e -> dispose(), escapeKey, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Rebuild and display HTML based on current search filter. */
    private void refreshHtml() {
        String filter = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        htmlPane.setText(buildHtml(sections, filter));
        htmlPane.setCaretPosition(0);
    }

    /** Build all shortcut sections. */
    private static ShortcutSection[] buildSections() {
        String mod = System.getProperty("os.name", "").toLowerCase().contains("mac")
                ? "\u2318" : "Ctrl";

        return new ShortcutSection[]{
            new ShortcutSection("Navigation & Assistant", new String[][]{
                {mod + " + L", "Toggle Assistant Panel"},
                {mod + " + Home", "Scroll to Top"},
                {mod + " + End", "Scroll to Bottom"},
                {"PgUp", "Scroll Up One Page"},
                {"PgDn", "Scroll Down One Page"},
            }),
            new ShortcutSection("Session Management", new String[][]{
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.NewSessionAction"), "New Session"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ReloadSessionAction"), "Reload Session"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.RenameSessionAction"), "Rename Session"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ArchiveSessionAction"), "Archive Session"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.RestartServerAction"), "Restart Server"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.SendMessageAction"), "Send Message"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.StopMessageAction"), "Stop Message"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleOptionsAction"), "Toggle Options"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ExportConversationAction"), "Export Session"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleMiniAssistantAction"), "Toggle Mini Assistant"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.AllowPermissionAction"), "Allow Once"},
            }),
            new ShortcutSection("Chat Input", new String[][]{
                {"Enter", "Send Message"},
                {"Shift + Enter", "Insert Newline"},
                {"/", "Slash Command Autocomplete"},
                {"Tab", "Switch Agent / Open Options"},
                {"Alt + \u2191", "Previous in History"},
                {"Alt + \u2193", "Next in History"},
            }),
            new ShortcutSection("Editing", new String[][]{
                {mod + " + Z", "Undo"},
                {mod + " + Y", "Redo"},
                {mod + " + R", "Search History"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.ToggleBlocksAction"), "Toggle Expand/Collapse All"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.SortLinesAction"), "Sort Lines Ascending"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.SortLinesDescAction"), "Sort Lines Descending"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.CompactJsonAction"), "Minify JSON"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.SearchWebAction"), "Search Web"},
                {ShortcutUtils.resolveShortcut("github.anandb.netbeans.ui.GoToFileAction"), "Jump to File"},
            }),
            new ShortcutSection("Stash Diff (Experimental)", new String[][]{
                {mod + " + Shift + L", "Open Stash Diff Viewer"},
                {mod + " + ,", "Previous Difference"},
                {mod + " + .", "Next Difference"},
            }),
        };
    }

    /** Build the full HTML from sections, filtered by the search query. */
    private static String buildHtml(ShortcutSection[] sections, String filter) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String border = theme.toHtmlHex(theme.tableBorder());
        String bg = theme.toHtmlHex(theme.tableBackground());
        String hdrBg = theme.toHtmlHex(theme.tableHeaderBackground());
        String altRowBg = theme.toHtmlHex(theme.tableRowAlternate());
        boolean isDark = theme.isDark();
        String sectionFg = isDark ? "#e0e0e0" : "#333";
        String kbdBg = isDark ? "#2a2d35" : "#e8e8ec";
        String kbdFg = isDark ? "#d0d0d0" : "#333";
        String kbdBorder = isDark ? "#444" : "#c0c0c0";
        String unassignedFg = isDark ? "#666" : "#999";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>")
          .append("body { font-family: sans-serif; font-size: 12px; margin: 0; padding: 0; }")
          .append(".kbd { display: inline-block; padding: 2px 7px; font-family: monospace; font-size: 12px;")
          .append("  font-weight: 600; background: ").append(kbdBg).append("; color: ").append(kbdFg).append(";")
          .append("  border: 1px solid ").append(kbdBorder).append("; border-radius: 4px;")
          .append("  box-shadow: 0 1px 0 ").append(kbdBorder).append("; white-space: nowrap; }")
          .append(".unassigned { font-style: italic; color: ").append(unassignedFg).append("; }")
          .append("</style></head><body>");

        for (ShortcutSection section : sections) {
            List<String[]> filtered = filterRows(section.rows, filter);
            if (filtered.isEmpty()) {
                continue;
            }
            renderSection(sb, section.title, filtered, border, bg, altRowBg, hdrBg, sectionFg, kbdBg, kbdFg, unassignedFg);
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    /** Filter rows by key or action matching the search query. */
    private static List<String[]> filterRows(String[][] rows, String filter) {
        List<String[]> result = new ArrayList<>();
        if (filter.isEmpty()) {
            for (String[] row : rows) {
                result.add(row);
            }
            return result;
        }
        for (String[] row : rows) {
            String key = row[0] != null ? row[0].toLowerCase() : "";
            String action = row[1] != null ? row[1].toLowerCase() : "";
            if (key.contains(filter) || action.contains(filter)) {
                result.add(row);
            }
        }
        return result;
    }

    /** Render a single section as a 2-column HTML table. */
    private static void renderSection(StringBuilder sb, String title, List<String[]> rows,
            String border, String bg, String altRowBg, String hdrBg, String sectionFg,
            String kbdBg, String kbdFg, String unassignedFg) {
        sb.append("<div style='border-top:2px solid ").append(border).append("; margin-top:14px;'>");
        sb.append("<p style='font-weight:bold;font-size:13px;margin:10px 0 6px;color:")
          .append(sectionFg).append(";'>").append(title).append("</p>");
        sb.append("<table border='1' bordercolor='").append(border)
          .append("' cellspacing='0' cellpadding='0'")
          .append(" style='border-collapse:collapse;width:100%;background:")
          .append(bg).append(";border-radius:6px;overflow:hidden;'>");
        sb.append("<tr>")
          .append("<th align='left' bgcolor='").append(hdrBg)
          .append("' style='padding:7px 10px;border:1px solid ").append(border)
          .append(";font-size:11px;text-transform:uppercase;letter-spacing:0.5px;width:200px;'>Key</th>")
          .append("<th align='left' bgcolor='").append(hdrBg)
          .append("' style='padding:7px 10px;border:1px solid ").append(border)
          .append(";font-size:11px;text-transform:uppercase;letter-spacing:0.5px;'>Action</th>")
          .append("</tr>");

        int i = 0;
        for (String[] row : rows) {
            String rowBg = (i % 2 == 0) ? bg : altRowBg;
            String keyHtml = formatKeyHtml(row[0], kbdBg, kbdFg, unassignedFg);

            sb.append("<tr style='background:").append(rowBg).append(";'>")
              .append("<td style='padding:7px 10px;border:1px solid ").append(border).append(";'>")
              .append(keyHtml).append("</td>")
              .append("<td style='padding:7px 10px;border:1px solid ").append(border).append(";'>")
              .append(row[1]).append("</td>")
              .append("</tr>");
            i++;
        }
        sb.append("</table></div>");
    }

    /** Format a key string as an HTML kbd element, or "Unassigned" if empty. */
    private static String formatKeyHtml(String key, String kbdBg, String kbdFg, String unassignedFg) {
        if (key == null || key.isEmpty()) {
            return "<span class='unassigned'>Unassigned</span>";
        }
        if ("None".equals(key)) {
            return "<span class='unassigned'>Unassigned</span>";
        }
        // Split multi-modifier shortcuts into individual kbd badges
        String[] parts = key.split("\\s*\\+\\s*");
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                html.append(" <span style='color:").append(unassignedFg).append(";'>+</span> ");
            }
            html.append("<span class='kbd'>").append(parts[i]).append("</span>");
        }
        return html.toString();
    }

    /** Holds a titled group of shortcut rows. */
    private static final class ShortcutSection {
        final String title;
        final String[][] rows;

        ShortcutSection(String title, String[][] rows) {
            this.title = title;
            this.rows = rows;
        }
    }
}
