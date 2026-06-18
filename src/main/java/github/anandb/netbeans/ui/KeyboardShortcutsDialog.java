package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;
import java.util.List;

import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;

import org.openide.util.NbBundle;
import org.openide.util.Utilities;

/**
 * Modal dialog listing all keyboard shortcuts supported by the plugin.
 * Rendered as styled HTML for clean presentation.
 */
final class KeyboardShortcutsDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static KeyboardShortcutsDialog currentInstance;

    private KeyboardShortcutsDialog(Frame owner) {
        super(owner, NbBundle.getMessage(KeyboardShortcutsDialog.class, "TITLE_KeyboardShortcuts"), false);
        setResizable(true);
        initComponents();
        pack();
        setMinimumSize(getPreferredSize());
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
        contentPanel.setBorder(new EmptyBorder(16, 20, 12, 20));

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

        contentPanel.add(htmlPane, BorderLayout.CENTER);

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
        String mod = System.getProperty("os.name", "").toLowerCase().contains("mac")
                ? "\u2318" : "Ctrl";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:sans-serif;font-size:12px;")
          .append("margin:0;padding:0;'>");

        table(sb, border, bg, hdrBg, "Input Area", new String[][]{
            {"Enter", "Send message"},
            {"Shift + Enter", "Insert newline"},
            {"Tab", "Switch agent / open options panel"},
            {"Alt + Up", "Previous message in history"},
            {"Alt + Down", "Next message in history"},
            {"/", "Trigger slash command autocomplete"},
            {mod + " + Z", "Undo"},
            {mod + " + Y", "Redo"},
        });

        table(sb, border, bg, hdrBg, "Chat Panel", new String[][]{
            {"Page Up", "Scroll up one page"},
            {"Page Down", "Scroll down one page"},
            {mod + " + Home", "Scroll to top"},
            {mod + " + End", "Scroll to bottom"},
        });

        table(sb, border, bg, hdrBg, "Global", new String[][]{
            {mod + " + L", "Toggle assistant panel"},
        });

        table(sb, border, bg, hdrBg, "Assignable Shortcuts (Tools > Keymap)", new String[][]{
            {resolveShortcut("Actions/Assistant/github-anandb-netbeans-ui-NewSessionAction"), "New Session"},
            {resolveShortcut("Actions/Assistant/github-anandb-netbeans-ui-ReloadSessionAction"), "Reload Conversation"},
            {resolveShortcut("Actions/Assistant/github-anandb-netbeans-ui-RenameSessionAction"), "Rename Session"},
            {resolveShortcut("Actions/Assistant/github-anandb-netbeans-ui-ArchiveSessionAction"), "Archive Session"},
            {resolveShortcut("Actions/Assistant/github-anandb-netbeans-ui-RestartServerAction"), "Restart Server"},
            {resolveShortcut("Actions/Assistant/github-anandb-netbeans-ui-SendMessageAction"), "Send Message"},
            {resolveShortcut("Actions/Assistant/github-anandb-netbeans-ui-StopMessageAction"), "Stop Message"},
            {resolveShortcut("Actions/Assistant/github-anandb-netbeans-ui-ToggleOptionsAction"), "Toggle Options"},
            {resolveShortcut("Actions/Assistant/github-anandb-netbeans-ui-ExportConversationAction"), "Export Conversation"},
            {resolveShortcut("Actions/Assistant/github-anandb-netbeans-ui-ToggleBlocksAction"), "Toggle Expand/Collapse All"},
        });

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String resolveShortcut(String actionPath) {
        List<? extends Action> actions = Utilities.actionsForPath(actionPath);
        if (actions != null && !actions.isEmpty()) {
            KeyStroke ks = (KeyStroke) actions.get(0).getValue(Action.ACCELERATOR_KEY);
            if (ks != null) {
                return ks.toString().replace("pressed ", "").replace("Released ", "");
            }
        }
        return "None";
    }

    private static void table(StringBuilder sb, String border, String bg,
            String hdrBg, String title, String[][] rows) {
        sb.append("<p style='font-weight:bold;font-size:13px;margin:12px 0 4px;'>")
          .append(title).append("</p>");
        sb.append("<table border='1' bordercolor='").append(border)
          .append("' cellspacing='0' cellpadding='6'")
          .append(" style='border-collapse:collapse;width:100%;")
          .append("background:").append(bg).append(";'>");
        sb.append("<tr><th align='left' bgcolor='").append(hdrBg)
          .append("' style='padding:6px 8px;border:1px solid ")
          .append(border).append(";'>Key</th>");
        sb.append("<th align='left' bgcolor='").append(hdrBg)
          .append("' style='padding:6px 8px;border:1px solid ")
          .append(border).append(";'>Action</th></tr>");
        for (String[] row : rows) {
            sb.append("<tr>");
            sb.append("<td style='padding:6px 8px;border:1px solid ")
              .append(border).append(";font-family:monospace;font-size:11px;'>")
              .append(row[0]).append("</td>");
            sb.append("<td style='padding:6px 8px;border:1px solid ")
              .append(border).append(";'>").append(row[1]).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
    }
}
