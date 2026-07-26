package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

import org.openide.util.NbBundle;
import github.anandb.netbeans.support.ShortcutUtils;

/**
 * Modal dialog listing all keyboard shortcuts supported by the plugin.
 * Uses JTable for all shortcut rows. Unassigned shortcuts are collapsed
 * into a single clickable summary row per section.
 */
// DSL-LEAF: keep imperative, wrap via UI.of(...) — JDialog modal form. Low-risk DSL pilot candidate
// (self-contained; no streaming/timer bridge). Migration target: DialogSpec family.
final class KeyboardShortcutsDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static KeyboardShortcutsDialog currentInstance;

    private JTextField searchField;
    private JPanel contentArea;
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

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(12, 16, 8, 16));

        // Header: title + search
        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.setOpaque(false);
        JLabel titleLabel = new JLabel(NbBundle.getMessage(KeyboardShortcutsDialog.class, "TITLE_KeyboardShortcuts"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize() + 3f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        searchField = new JTextField(16);
        searchField.putClientProperty("JTextField.placeholderText",
                NbBundle.getMessage(KeyboardShortcutsDialog.class, "LBL_FilterShortcuts"));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { rebuildContent(); }
            @Override
            public void removeUpdate(DocumentEvent e) { rebuildContent(); }
            @Override
            public void changedUpdate(DocumentEvent e) { rebuildContent(); }
        });
        headerPanel.add(searchField, BorderLayout.EAST);
        root.add(headerPanel, BorderLayout.NORTH);

        // Scrollable content
        contentArea = new JPanel();
        contentArea.setLayout(new javax.swing.BoxLayout(contentArea, javax.swing.BoxLayout.Y_AXIS));
        contentArea.setOpaque(false);
        rebuildContent();

        JScrollPane scrollPane = new JScrollPane(contentArea);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scrollPane, BorderLayout.CENTER);

        // Close hint
        JLabel closeHint = new JLabel(NbBundle.getMessage(KeyboardShortcutsDialog.class, "LBL_CloseHint"));
        closeHint.setFont(closeHint.getFont().deriveFont(Font.ITALIC, closeHint.getFont().getSize() - 1f));
        closeHint.setForeground(closeHint.getForeground().brighter());
        closeHint.setHorizontalAlignment(SwingConstants.CENTER);
        closeHint.setBorder(new EmptyBorder(4, 0, 0, 0));
        root.add(closeHint, BorderLayout.SOUTH);

        setContentPane(root);

        KeyStroke escapeKey = KeyStroke.getKeyStroke("ESCAPE");
        getRootPane().registerKeyboardAction(e -> dispose(), escapeKey, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Rebuild all section content from scratch. */
    private void rebuildContent() {
        String filter = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        boolean searching = !filter.isEmpty();
        contentArea.removeAll();

        for (ShortcutSection section : sections) {
            List<String[]> filtered = filterRows(section.rows, filter);
            if (filtered.isEmpty()) {
                continue;
            }
            addSection(contentArea, section.title, filtered, searching);
            JComponent strut = (JComponent) Box.createVerticalStrut(6);
            strut.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentArea.add(strut);
        }

        contentArea.revalidate();
        contentArea.repaint();
    }

    /** Add a section: title label + JTable(s). Collapsible for unassigned when not searching. */
    private void addSection(JPanel container, String title, List<String[]> rows, boolean searching) {
        List<String[]> assigned = new ArrayList<>();
        List<String[]> unassigned = new ArrayList<>();
        for (String[] row : rows) {
            (isUnassigned(row[0]) ? unassigned : assigned).add(row);
        }

        // Section title — left-aligned, larger than cell text
        JLabel sectionLabel = new JLabel(title);
        sectionLabel.setFont(sectionLabel.getFont().deriveFont(Font.BOLD, sectionLabel.getFont().getSize() + 1f));
        sectionLabel.setForeground(isDark() ? java.awt.Color.decode("#e0e0e0") : java.awt.Color.decode("#333"));
        sectionLabel.setBorder(new EmptyBorder(10, 0, 6, 0));
        // FlowLayout.LEFT prevents BoxLayout from stretching the label
        JPanel titleWrapper = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        titleWrapper.setOpaque(false);
        titleWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleWrapper.getPreferredSize().height));
        titleWrapper.add(sectionLabel);
        container.add(titleWrapper);

        // Assigned rows: always a JTable
        if (!assigned.isEmpty()) {
            container.add(buildTable(assigned));
        }

        // Unassigned rows
        if (!unassigned.isEmpty()) {
            if (searching) {
                // Show all matching rows directly
                container.add(buildTable(unassigned));
            } else {
                // Collapsed: clickable summary + hidden table
                container.add(buildUnassignedCollapse(unassigned, assigned.isEmpty()));
            }
        }
    }

    /** Build a styled JTable for the given rows. */
    private static JTable buildTable(List<String[]> rows) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String bgHex = theme.toHtmlHex(theme.tableBackground());
        String altHex = theme.toHtmlHex(theme.tableRowAlternate());
        String hdrHex = theme.toHtmlHex(theme.tableHeaderBackground());
        String borderHex = theme.toHtmlHex(theme.tableBorder());
        boolean isDark = theme.isDark();
        String kbdBg = isDark ? "#2a2d35" : "#e8e8ec";
        String kbdFg = isDark ? "#d0d0d0" : "#333";
        String unassignedFg = isDark ? "#666" : "#999";

        java.awt.Color bg = java.awt.Color.decode(bgHex);
        java.awt.Color alt = java.awt.Color.decode(altHex);
        java.awt.Color hdrBg = java.awt.Color.decode(hdrHex);
        java.awt.Color border = java.awt.Color.decode(borderHex);
        java.awt.Color fg = isDark ? java.awt.Color.decode("#e0e0e0") : java.awt.Color.decode("#333");

        DefaultTableModel model = new DefaultTableModel(rows.size(), 2) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (int i = 0; i < rows.size(); i++) {
            model.setValueAt(rows.get(i)[0], i, 0);
            model.setValueAt(rows.get(i)[1], i, 1);
        }

        JTable table = new JTable(model);
        table.setAlignmentX(Component.LEFT_ALIGNMENT);
        table.setShowGrid(true);
        table.setGridColor(border);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setRowHeight(32);
        table.setTableHeader(new JTableHeader(table.getColumnModel()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = 28;
                return d;
            }
        });
        table.getTableHeader().setBackground(hdrBg);
        table.getTableHeader().setForeground(fg);
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.PLAIN, table.getFont().getSize() - 2f));
        table.getTableHeader().setBorder(new MatteBorder(0, 0, 1, 0, border));

        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(180);
        cm.getColumn(0).setMaxWidth(260);
        cm.getColumn(1).setPreferredWidth(300);

        // Header renderer
        DefaultTableCellRenderer hdrRenderer = new DefaultTableCellRenderer();
        hdrRenderer.setBackground(hdrBg);
        hdrRenderer.setForeground(fg);
        hdrRenderer.setBorder(new MatteBorder(0, 0, 1, 0, border));
        hdrRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        hdrRenderer.setHorizontalTextPosition(SwingConstants.LEFT);
        hdrRenderer.setOpaque(true);
        cm.getColumn(0).setHeaderRenderer(hdrRenderer);
        cm.getColumn(1).setHeaderRenderer(hdrRenderer);

        // Cell renderer: kbd badge for key column, unassigned for empty keys
        DefaultTableCellRenderer keyRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean focus, int row, int column) {
                String key = val != null ? val.toString() : "";
                if (isUnassigned(key)) {
                    setText("<html><i style='color:" + unassignedFg + ";'>Unassigned</i></html>");
                } else {
                    setText("<html>" + renderKbdBadge(key, kbdBg, kbdFg, unassignedFg) + "</html>");
                }
                setBackground(sel ? t.getSelectionBackground() : (row % 2 == 0 ? bg : alt));
                setForeground(fg);
                setBorder(new EmptyBorder(4, 4, 4, 4));
                return this;
            }
        };
        cm.getColumn(0).setCellRenderer(keyRenderer);

        // Cell renderer: alternating row background for action column
        DefaultTableCellRenderer actionRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, val, sel, focus, row, column);
                setBackground(sel ? t.getSelectionBackground() : (row % 2 == 0 ? bg : alt));
                setBorder(new EmptyBorder(4, 4, 4, 4));
                return this;
            }
        };
        cm.getColumn(1).setCellRenderer(actionRenderer);

        // Wrap in panel with no border (JScrollPane manages its own)
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBorder(new EmptyBorder(0, 0, 0, 0));
        wrapper.add(table, BorderLayout.CENTER);
        // Fix: prevent JTable from stealing scroll from the outer JScrollPane
        table.setPreferredScrollableViewportSize(table.getPreferredSize());
        wrapper.setPreferredSize(new Dimension(600, table.getPreferredSize().height + 28));

        return table;
    }

    /**
     * Build a collapsible panel for unassigned shortcuts.
     * Clicking the summary label toggles visibility of the detail table.
     */
    private static JPanel buildUnassignedCollapse(List<String[]> unassigned, boolean addTopSpacing) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        String borderHex = theme.toHtmlHex(theme.tableBorder());
        String bgHex = theme.toHtmlHex(theme.tableBackground());
        String kbdFg = theme.isDark() ? "#d0d0d0" : "#333";

        java.awt.Color border = java.awt.Color.decode(borderHex);
        java.awt.Color bg = java.awt.Color.decode(bgHex);
        java.awt.Color fg = java.awt.Color.decode(kbdFg);
        java.awt.Color accentFg = isDark() ? java.awt.Color.decode("#7eb8da") : java.awt.Color.decode("#2a6496");
        java.awt.Color accentBg = isDark() ? java.awt.Color.decode("#1e2a35") : java.awt.Color.decode("#e8f0f8");

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (addTopSpacing) {
            wrapper.setBorder(new EmptyBorder(0, 0, 0, 0));
        }

        // Summary label — full width, looks like a table row
        String count = unassigned.size() + (unassigned.size() == 1 ? " Action" : " Actions");
        JLabel summary = new JLabel("  Unassigned \u00a0\u00a0\u00a0 " + count + "  \u25BC");
        summary.setFont(summary.getFont().deriveFont(Font.BOLD, summary.getFont().getSize() + 1f));
        summary.setForeground(accentFg);
        summary.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                new MatteBorder(1, 1, 1, 1, border),
                new EmptyBorder(6, 4, 6, 4)));
        summary.setOpaque(true);
        summary.setBackground(accentBg);
        summary.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        // Detail table — hidden by default
        JTable detailTable = buildTable(unassigned);
        JPanel detailWrapper = new JPanel(new BorderLayout());
        detailWrapper.setOpaque(false);
        detailWrapper.add(detailTable, BorderLayout.CENTER);
        detailWrapper.setVisible(false);

        summary.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean show = !detailWrapper.isVisible();
                detailWrapper.setVisible(show);
                // Update arrow: ▼ collapsed → ▲ expanded
                String text = summary.getText();
                summary.setText(text.substring(0, text.length() - 1) + (show ? "\u25B2" : "\u25BC"));
                summary.revalidate();
                summary.repaint();
                // Adjust preferred size so scroll pane updates
                wrapper.revalidate();
            }
        });

        wrapper.add(summary, BorderLayout.NORTH);
        wrapper.add(detailWrapper, BorderLayout.CENTER);
        return wrapper;
    }

    /** Format a key string as an HTML kbd badge span. */
    private static String renderKbdBadge(String key, String kbdBg, String kbdFg, String unassignedFg) {
        String[] parts = key.split("\\s*\\+\\s*");
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                html.append(" <span style='color:").append(unassignedFg).append(";'>+</span> ");
            }
            html.append("<span style='display:inline-block;padding:1px 5px;font-family:monospace;")
                .append("font-size:12px;font-weight:600;background:").append(kbdBg).append(";color:").append(kbdFg).append(";")
                .append("border:1px solid ").append(kbdFg).append(";border-radius:3px;white-space:nowrap;'>")
                .append(parts[i]).append("</span>");
        }
        return html.toString();
    }

    /** Filter rows by key or action matching the search query. */
    private static List<String[]> filterRows(String[][] rows, String filter) {
        List<String[]> result = new ArrayList<>();
        if (filter.isEmpty()) {
            for (String[] row : rows) { result.add(row); }
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

    /** Check if a key value represents an unassigned shortcut. */
    private static boolean isUnassigned(String key) {
        return key == null || key.isEmpty() || "None".equals(key);
    }

    private static boolean isDark() {
        return ThemeManager.getCurrentTheme().isDark();
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
                {"Alt + Up", "Previous in History"},
                {"Alt + Down", "Next in History"},
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
