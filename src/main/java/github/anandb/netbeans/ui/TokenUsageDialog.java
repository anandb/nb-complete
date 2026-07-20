package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;

import org.openide.util.Lookup;

import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ProcessTerminator;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import static github.anandb.netbeans.ui.UIUtils.MONO_STACK;
import java.awt.GridBagConstraints;

// DSL-LEAF: a standalone dialog for token usage stats.
// Built imperatively — no need for the full SwingTree DSL.
public class TokenUsageDialog extends JDialog {

    private static final Logger LOG = Logger.from(TokenUsageDialog.class);
    private static final long serialVersionUID = 1L;

    private static final String PROJECT_CURRENT = "Current Project";
    private static final String PROJECT_ALL = "All";
    private static final String[] PROJECT_OPTIONS = { PROJECT_CURRENT, PROJECT_ALL };
    private static final java.util.regex.Pattern ANSI_ESCAPE =
        java.util.regex.Pattern.compile("\u001b\\[[0-9;]*[a-zA-Z~]|\u001b\\][^\u0007]*\u0007");

    private final JSpinner daysSpinner;
    private final JComboBox<String> projectCombo;
    private final FitEditorPane statsPane;
    private final JButton refreshBtn;
    private final JScrollPane scrollPane;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Process currentProcess;
    private javax.swing.Timer autoRefreshTimer;
    private boolean firstRefresh = true;

    public TokenUsageDialog(java.awt.Frame owner) {
        super(owner, "Token Stats", false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(540, 460));
        setResizable(true);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        ColorTheme theme = ThemeManager.getCurrentTheme();
        content.setBackground(theme.background());

        // --- Title ---
        JLabel titleLabel = new JLabel("Token Stats");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        content.add(titleLabel, BorderLayout.NORTH);

        // --- Form (single row: Days | Model | Project | Refresh) ---
        JPanel formPanel = new JPanel(new java.awt.GridBagLayout());
        formPanel.setOpaque(false);
        java.awt.Insets ins = new java.awt.Insets(0, 2, 0, 2);

        // Days (fixed)
        daysSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(daysSpinner, "#");
        editor.getTextField().setColumns(3);
        daysSpinner.setEditor(editor);
        daysSpinner.setPreferredSize(new Dimension(80, 28));
        daysSpinner.setMaximumSize(new Dimension(80, 28));

        JLabel daysLabel = new JLabel("Days:");
        daysLabel.setLabelFor(daysSpinner);
        formPanel.add(daysLabel,  new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, ins, 0, 0));
        formPanel.add(daysSpinner, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, ins, 0, 0));

        // Project (flexible — fills available space)
        projectCombo = new JComboBox<>(PROJECT_OPTIONS);
        projectCombo.setPreferredSize(new Dimension(140, 28));
        projectCombo.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (PROJECT_CURRENT.equals(value) && !isCurrentProjectAvailable()) {
                    setEnabled(false);
                    if (!isSelected) {
                        setForeground(Color.GRAY);
                    }
                }
                return this;
            }
        });
        // Revert to "All" if "Current Project" is somehow selected when unavailable
        projectCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED
                    && PROJECT_CURRENT.equals(e.getItem()) && !isCurrentProjectAvailable()) {
                projectCombo.setSelectedItem(PROJECT_ALL);
            }
        });
        // Default to "All" when "Current Project" is unavailable
        if (!isCurrentProjectAvailable()) {
            projectCombo.setSelectedItem(PROJECT_ALL);
        }

        JLabel projectLabel = new JLabel("Project:");
        projectLabel.setLabelFor(projectCombo);
        formPanel.add(projectLabel, new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, ins, 0, 0));
        formPanel.add(projectCombo, new GridBagConstraints(3, 0, 1, 1, 1.0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, ins, 0, 0));

        // Refresh button (fixed, rightmost)
        refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(this::onRefresh);
        formPanel.add(refreshBtn, new GridBagConstraints(4, 0, 1, 1, 0, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, ins, 0, 0));

        content.add(formPanel, BorderLayout.BEFORE_FIRST_LINE);

        // --- Stats area ---
        statsPane = new FitEditorPane();
        statsPane.putClientProperty(JTextPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        statsPane.setEditable(false);
        statsPane.setContentType("text/html");
        statsPane.setOpaque(true);
        statsPane.setBackground(theme.bubbleAssistant());
        statsPane.setForeground(theme.foreground());
        statsPane.setDoubleBuffered(true);
        statsPane.setMargin(new Insets(0, 0, 0, 0));
        statsPane.setBorder(new EmptyBorder(8, 8, 8, 8));
        statsPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsPane.setText(buildPlaceholderHtml(theme));

        scrollPane = new JScrollPane(statsPane);
        scrollPane.setPreferredSize(new Dimension(500, 260));
        scrollPane.getViewport().setBackground(theme.bubbleAssistant());
        content.add(scrollPane, BorderLayout.CENTER);

        // --- Close button (centered) ---
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        btnPanel.setOpaque(false);
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        btnPanel.add(closeBtn);

        content.add(btnPanel, BorderLayout.SOUTH);

        // --- ESC to close ---
        getRootPane().registerKeyboardAction(
            new AbstractAction() {
                private static final long serialVersionUID = 1L;
                @Override public void actionPerformed(ActionEvent e) { dispose(); }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Returns true when there is at least one open project and an active session. */
    private static boolean isCurrentProjectAvailable() {
        PlatformBridge bridge = Lookup.getDefault().lookup(PlatformBridge.class);
        if (bridge == null) return false;
        org.netbeans.api.project.Project[] projects = bridge.projectContext().getAllOpenProjects();
        if (projects == null || projects.length == 0) return false;
        SessionQuery sq = Lookup.getDefault().lookup(SessionQuery.class);
        return sq != null && sq.getCurrentSessionId() != null;
    }

    private void onRefresh(ActionEvent e) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        int days = (int) daysSpinner.getValue();
        String project = (String) projectCombo.getSelectedItem();
        refreshBtn.setEnabled(false);
        ColorTheme currentTheme = ThemeManager.getCurrentTheme();
        statsPane.setText(buildPlaceholderHtml(currentTheme, "Fetching stats..."));

        new Thread(() -> {
            try {
                String projectDir = null;
                if (PROJECT_CURRENT.equals(project)) {
                    SessionQuery sq = Lookup.getDefault().lookup(SessionQuery.class);
                    if (sq != null) {
                        projectDir = sq.getCurrentSessionDirectory();
                    }
                }
                String result = ANSI_ESCAPE.matcher(runStatsCommand(days, projectDir)).replaceAll("");
                String styledHtml = convertStatsToHtml(result, currentTheme);
                SwingUtilities.invokeLater(() -> {
                    statsPane.setText(styledHtml);
                    if (firstRefresh) {
                        firstRefresh = false;
                        autoSizeInitial();
                    }
                    // Scroll to top after layout settles
                    SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
                });
            } catch (Exception ex) {
                LOG.log(java.util.logging.Level.WARNING, "Failed to fetch token usage stats", ex);
                SwingUtilities.invokeLater(() -> {
                    statsPane.setText(buildPlaceholderHtml(currentTheme, "Error: " + ExceptionUtils.getMessage(ex)));
                    SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
                });
            } finally {
                currentProcess = null;
                SwingUtilities.invokeLater(() -> refreshBtn.setEnabled(true));
                running.set(false);
            }
        }, "token-stats").start();
    }

    private String runStatsCommand(int days, String projectDir) throws Exception {
        String binary = BinaryResolver.resolveExecutablePath();
        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        cmd.add("stats");
        cmd.add("--days");
        cmd.add(String.valueOf(days));
        cmd.add("--models");
        if (projectDir != null) {
            cmd.add("--project");
            cmd.add("");
        }

        LOG.info("Running opencode stats command: {0}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            pb.environment().put("LANG", "C");
            pb.environment().put("LC_ALL", "C");
            pb.environment().put("NO_COLOR", "1");
        }
        pb.redirectErrorStream(true);
        if (projectDir != null) {
            File dir = new File(projectDir);
            if (dir.isDirectory()) {
                pb.directory(dir);
            }
        }
        Process proc = pb.start();
        currentProcess = proc;

        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            String output = sb.toString().trim();
            throw new RuntimeException("opencode stats exited with code "
                + exitCode + (output.isEmpty() ? "" : ": " + output));
        }
        return sb.toString().trim();
    }

    /** Gracefully terminates any running opencode processes. */
    private void cancelProcess() {
        ProcessTerminator.terminate(currentProcess);
        currentProcess = null;
    }

    /** Sizes the dialog to 90% parent height with reasonable width for table content. */
    private void autoSizeInitial() {
        java.awt.Window parent = SwingUtilities.getWindowAncestor(this);
        int h = parent != null ? (int)(parent.getHeight() * 0.9) : 520;
        setSize(new Dimension(700, h));
        revalidate();
    }

    // ---- Box-drawing to HTML table conversion ----

    /** Extracts text content from between │ delimiters in a box-drawing row. */
    private static String extractBoxContent(String line) {
        int first = line.indexOf('│');
        int last = line.lastIndexOf('│');
        if (first >= 0 && last > first) {
            return line.substring(first + 1, last).trim();
        }
        return null;
    }

    private static boolean containsNonAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) {
                return true;
            }
        }
        return false;
    }

    /** Parses a stats row content into [label, value] or [label, progress, percentage]. */
    static String[] parseStatsRow(String content) {
        if (containsNonAscii(content)) {
            int idx = -1;
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) > 127) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                String labelPart = content.substring(0, idx).trim();
                String rest = content.substring(idx).trim();
                String[] restParts = rest.split("\\s+", 2);
                if (restParts.length == 2) {
                    return new String[]{labelPart, restParts[0], restParts[1]};
                } else {
                    return new String[]{labelPart, rest, ""};
                }
            }
        }

        // Split on 2+ spaces (box-drawing uses fixed-width padding)
        String[] parts = content.split("\\s{2,}");
        if (parts.length >= 2) {
            String label = parts[0].trim();
            // Simple key-value: label is first, value is last
            return new String[]{label, parts[parts.length - 1].trim()};
        }
        // Single value (model name sub-header, etc.)
        return new String[]{content, ""};
    }

    /** Escapes HTML special characters. */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Converts box-drawing stats output into themed HTML tables. */
    static String convertStatsToHtml(String rawText, ColorTheme theme) {
        String[] lines = rawText.split("\n", -1);
        StringBuilder sb = new StringBuilder(2048);
        String bg = theme.toHtmlHex(theme.bubbleAssistant());
        String fg = theme.toHtmlHex(theme.assistantForeground());
        String borderColor = theme.toHtmlHex(theme.tableBorder());
        String headerBg = theme.toHtmlHex(theme.tableHeaderBackground());
        String tableBg = theme.toHtmlHex(theme.tableBackground());
        String altBg = theme.toHtmlHex(theme.tableRowAlternate());
        String linkColor = ThemeManager.isDark() ? "#589DF6" : "#268BD2";

        sb.append("<html><head><style>")
          .append("html,body{margin:0;padding:8px;background:").append(bg)
          .append(";color:").append(fg).append(";font-family:")
          .append(MONO_STACK).append(";font-size:13px;line-height:1.4;}")
          .append("table{border-collapse:collapse;width:100%;margin:8px 0;background:")
          .append(tableBg).append(";}")
          .append("th{background:").append(headerBg).append(";padding:8px;border:1px solid ")
          .append(borderColor).append(";text-align:left;font-weight:bold;}")
          .append("td{padding:8px;border:1px solid ").append(borderColor)
          .append(";vertical-align:top;}")
          .append("a{color:").append(linkColor).append(";text-decoration:none;}")
          .append("</style></head><body>");

        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (!line.contains("\u250C")) {
                i++;
                continue;
            } // ┌
            i++;

            // Section title
            String title = "";
            if (i < lines.length) {
                String t = extractBoxContent(lines[i]);
                if (t != null) title = t;
                i++;
            }
            // Skip separator (├───┤)
            if (i < lines.length && lines[i].contains("\u251C")) { i++; } // ├

            // Collect data rows until section end
            List<String[]> rows = new ArrayList<>();
            while (i < lines.length) {
                String dl = lines[i];
                if (dl.contains("\u2514")) break; // └
                String content = extractBoxContent(dl);
                if (content != null && !content.isEmpty()) {
                    rows.add(parseStatsRow(content));
                }
                i++;
            }
            // Skip past └─ line — outer loop skips blanks and finds next ┌─
            i++;

            // Render table
            sb.append("<table><tr><th colspan='3'>").append(escapeHtml(title)).append("</th></tr>");
            boolean alt = false;
            for (String[] row : rows) {
                if (row.length == 2 && row[1].isEmpty()) {
                    // Sub-header row (model name)
                    sb.append("<tr><td colspan='3' style='font-weight:bold;background:")
                      .append(headerBg).append("'>").append(escapeHtml(row[0])).append("</td></tr>");
                    alt = false;
                } else if (row.length == 3) {
                    // Progress bar row
                    String rowStyle = "";
                    if (alt) rowStyle += "style='background-color: " + altBg + "'";
                    sb.append("<tr ").append(rowStyle).append(">")
                      .append("<td style='white-space:nowrap;'>").append(escapeHtml(row[0])).append("</td>")
                      .append("<td style='text-align:right;'>").append(escapeHtml(row[1])).append("</td>")
                      .append("<td style='text-align:right;white-space:nowrap;'>").append(escapeHtml(row[2])).append("</td>")
                      .append("</tr>");
                    alt = !alt;
                } else {
                    // Simple key-value row (length 2)
                    boolean isTotal = "Total Cost".equals(row[0]);
                    String rowStyle = isTotal ? "font-weight:bold;" : "";
                    if (alt) rowStyle += "background-color: " + altBg + ";";
                    String trAttr = rowStyle.isEmpty() ? "" : " style='" + rowStyle + "'";
                    sb.append("<tr").append(trAttr).append(">")
                      .append("<td colspan='2' style='white-space:nowrap;'>").append(escapeHtml(row[0])).append("</td>")
                      .append("<td style='text-align:right;'>").append(escapeHtml(row[1])).append("</td>")
                      .append("</tr>");
                    alt = !alt;
                }
            }
            sb.append("</table>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    /** Builds a simple placeholder HTML message for the stats pane. */
    private static String buildPlaceholderHtml(ColorTheme theme) {
        return buildPlaceholderHtml(theme, "Press Refresh to fetch token usage stats.");
    }

    private static String buildPlaceholderHtml(ColorTheme theme, String message) {
        String fg = theme.toHtmlHex(theme.foreground());
        return "<html><body style='margin:0;padding:8px;color:" + fg
            + ";font-family:sans-serif;font-size:13px;'>"
            + escapeHtml(message) + "</body></html>";
    }

    public static void show(java.awt.Frame parent) {
        SwingUtilities.invokeLater(() -> {
            TokenUsageDialog dlg = new TokenUsageDialog(parent);
            // Auto-refresh 1s after show so UI paints first
            dlg.autoRefreshTimer = new javax.swing.Timer(1000, e -> dlg.onRefresh(null));
            dlg.autoRefreshTimer.setRepeats(false);
            dlg.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) {
                    if (dlg.autoRefreshTimer != null) dlg.autoRefreshTimer.stop();
                    dlg.cancelProcess();
                }
            });
            dlg.autoRefreshTimer.start();
            dlg.setVisible(true);
        });
    }
}
