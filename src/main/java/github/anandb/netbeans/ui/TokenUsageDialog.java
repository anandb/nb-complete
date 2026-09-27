package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import javax.swing.AbstractAction;
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
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.netbeans.api.project.Project;

import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;

import java.util.List;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.UsageStatsFormat;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import static github.anandb.netbeans.ui.UIUtils.MONO_STACK;

/**
 * Token and cost panel. Renders the OVERVIEW and COST &amp; TOKENS blocks from
 * the local {@link UsageStatsStore} — no harness subprocess is spawned, and the
 * entry point (the currency button next to the attachment icon) is unchanged.
 */
public class TokenUsageDialog extends JDialog {

    private static final Logger LOG = Logger.from(TokenUsageDialog.class);
    private static final long serialVersionUID = 1L;

    private final JSpinner daysSpinner;
    private final JComboBox<String> projectCombo;
    private final FitEditorPane statsPane;
    private final JButton refreshBtn;
    private final JScrollPane scrollPane;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Timer autoRefreshTimer;
    private boolean firstRefresh = true;

    /** Background worker for the local query and HTML rendering. */
    private static final RequestProcessor STATS_RP = new RequestProcessor("token-stats", 1);

    public TokenUsageDialog(Frame owner) {
        super(owner, NbBundle.getMessage(TokenUsageDialog.class, "LBL_TokenStats"), false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(540, 460));
        setResizable(true);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        ColorTheme theme = ThemeManager.getCurrentTheme();
        content.setBackground(theme.background());

        // --- Title ---
        JLabel titleLabel = new JLabel(NbBundle.getMessage(TokenUsageDialog.class, "LBL_TokenStats"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        content.add(titleLabel, BorderLayout.NORTH);

        // --- Form (single row: Days | Project | Refresh) ---
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        Insets ins = new Insets(0, 2, 0, 2);

        // Days (fixed)
        daysSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(daysSpinner, "#");
        editor.getTextField().setColumns(3);
        daysSpinner.setEditor(editor);
        daysSpinner.setPreferredSize(new Dimension(80, 28));
        daysSpinner.setMaximumSize(new Dimension(80, 28));

        JLabel daysLabel = new JLabel(NbBundle.getMessage(TokenUsageDialog.class, "LBL_Days"));
        daysLabel.setLabelFor(daysSpinner);
        formPanel.add(daysLabel,  new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, ins, 0, 0));
        formPanel.add(daysSpinner, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, ins, 0, 0));

        // Project (flexible — fills available space)
        projectCombo = new JComboBox<>(new String[]{
            NbBundle.getMessage(TokenUsageDialog.class, "LBL_CurrentProject"),
            NbBundle.getMessage(TokenUsageDialog.class, "LBL_AllProjects")});
        projectCombo.setPreferredSize(new Dimension(140, 28));
        projectCombo.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (NbBundle.getMessage(TokenUsageDialog.class, "LBL_CurrentProject").equals(value) && !isCurrentProjectAvailable()) {
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
                    && NbBundle.getMessage(TokenUsageDialog.class, "LBL_CurrentProject").equals(e.getItem()) && !isCurrentProjectAvailable()) {
                projectCombo.setSelectedItem(NbBundle.getMessage(TokenUsageDialog.class, "LBL_AllProjects"));
            }
        });
        // Default to "All" when "Current Project" is unavailable
        if (!isCurrentProjectAvailable()) {
            projectCombo.setSelectedItem(NbBundle.getMessage(TokenUsageDialog.class, "LBL_AllProjects"));
        }

        JLabel projectLabel = new JLabel(NbBundle.getMessage(TokenUsageDialog.class, "LBL_Project"));
        projectLabel.setLabelFor(projectCombo);
        formPanel.add(projectLabel, new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, ins, 0, 0));
        formPanel.add(projectCombo, new GridBagConstraints(3, 0, 1, 1, 1.0, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, ins, 0, 0));

        // Refresh button (fixed, rightmost)
        refreshBtn = new JButton(NbBundle.getMessage(TokenUsageDialog.class, "LBL_Refresh"));
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
        JButton closeBtn = new JButton(NbBundle.getMessage(TokenUsageDialog.class, "LBL_Close"));
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
        Project[] projects = bridge.projectContext().getAllOpenProjects();
        if (projects == null || projects.length == 0) return false;
        SessionQuery sq = Lookup.getDefault().lookup(SessionQuery.class);
        return sq != null && sq.getCurrentSessionId() != null;
    }

    private void onRefresh(ActionEvent e) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        int days = (int) daysSpinner.getValue();
        boolean currentProject = NbBundle.getMessage(TokenUsageDialog.class, "LBL_CurrentProject")
                .equals(projectCombo.getSelectedItem());
        refreshBtn.setEnabled(false);
        ColorTheme currentTheme = ThemeManager.getCurrentTheme();
        statsPane.setText(buildPlaceholderHtml(currentTheme, NbBundle.getMessage(TokenUsageDialog.class, "LBL_FetchingStats")));

        STATS_RP.post(() -> {
            try {
                String projectDir = null;
                if (currentProject) {
                    SessionQuery sq = Lookup.getDefault().lookup(SessionQuery.class);
                    if (sq != null) {
                        projectDir = sq.getCurrentSessionDirectory();
                    }
                }
                UsageStatsStore store = Lookup.getDefault().lookup(UsageStatsStore.class);
                UsageSummary summary = store != null
                        ? store.query(days, projectDir, System.currentTimeMillis())
                        : UsageSummary.empty();
                List<GroupTotals> byModel = store != null
                        ? store.queryGrouped(days, projectDir, System.currentTimeMillis(),
                                UsageStatsStore.GroupBy.MODEL)
                        : List.of();
                List<GroupTotals> byAgent = store != null
                        ? store.queryGrouped(days, projectDir, System.currentTimeMillis(),
                                UsageStatsStore.GroupBy.HARNESS)
                        : List.of();
                String styledHtml = renderSummaryHtml(summary, byModel, byAgent, currentTheme);
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
                LOG.log(Level.WARNING, "Failed to read local token usage stats", ex);
                SwingUtilities.invokeLater(() -> {
                    statsPane.setText(buildPlaceholderHtml(currentTheme,
                            NbBundle.getMessage(TokenUsageDialog.class, "ERR_StatsError",
                                    ExceptionUtils.getMessage(ex))));
                    SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
                });
            } finally {
                SwingUtilities.invokeLater(() -> refreshBtn.setEnabled(true));
                running.set(false);
            }
        });
    }

    /** Sizes the dialog to 90% parent height with reasonable width for table content. */
    private void autoSizeInitial() {
        Window parent = SwingUtilities.getWindowAncestor(this);
        int h = parent != null ? (int)(parent.getHeight() * 0.9) : 520;
        setSize(new Dimension(700, h));
        revalidate();
    }

    // ---- Summary to HTML ----

    /** Escapes HTML special characters. */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Renders the OVERVIEW and COST &amp; TOKENS blocks as themed HTML tables
     * from the aggregated summary, then the breakdown tables grouped by model
     * and by agent. Group tables are omitted when the group list is empty.
     */
    static String renderSummaryHtml(UsageSummary summary, List<GroupTotals> byModel,
                                    List<GroupTotals> byAgent, ColorTheme theme) {
        String bg = theme.toHtmlHex(theme.bubbleAssistant());
        String fg = theme.toHtmlHex(theme.assistantForeground());
        String borderColor = theme.toHtmlHex(theme.tableBorder());
        String headerBg = theme.toHtmlHex(theme.tableHeaderBackground());
        String tableBg = theme.toHtmlHex(theme.tableBackground());
        String altBg = theme.toHtmlHex(theme.tableRowAlternate());

        StringBuilder sb = new StringBuilder(2048);
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
          .append("</style></head><body>");

        appendTable(sb, "LBL_Overview", headerBg, altBg, new String[][]{
            {"LBL_Sessions", UsageStatsFormat.thousands(summary.sessions())},
            {"LBL_Messages", UsageStatsFormat.thousands(summary.messages())},
            {"LBL_StatsDays", UsageStatsFormat.thousands(summary.days())},
        });
        appendTable(sb, "LBL_CostTokens", headerBg, altBg, new String[][]{
            {"LBL_TotalCost", UsageStatsFormat.cost(summary.totalCost())},
            {"LBL_AvgCostDay", UsageStatsFormat.cost(summary.avgCostPerDay())},
            {"LBL_AvgTokensSession", UsageStatsFormat.abbreviated(summary.avgTokensPerSession())},
            {"LBL_MedianTokensSession", UsageStatsFormat.abbreviated(summary.medianTokensPerSession())},
            {"LBL_Input", UsageStatsFormat.abbreviated(summary.inputTokens())},
            {"LBL_Output", UsageStatsFormat.abbreviated(summary.outputTokens())},
            {"LBL_CacheRead", UsageStatsFormat.abbreviated(summary.cachedReadTokens())},
        });

        String[] groupHeaders = {"LBL_Sessions", "LBL_Messages", "LBL_ToolCalls",
            "LBL_Input", "LBL_Output", "LBL_CacheRead", "LBL_TotalCost"};
        if (!byModel.isEmpty()) {
            appendGroupTable(sb, "LBL_ByModel", groupHeaders, byModel, headerBg, altBg);
        }
        if (!byAgent.isEmpty()) {
            appendGroupTable(sb, "LBL_ByAgent", groupHeaders, byAgent, headerBg, altBg);
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Renders one breakdown table: the group key in the header column, then
     * Sessions, Messages, Tool Calls, Input, Output, Cache Read and Total
     * Cost columns per group. Costs carry four decimals to keep the small
     * per-group amounts readable.
     */
    private static void appendGroupTable(StringBuilder sb, String titleKey, String[] headerKeys,
                                         List<GroupTotals> rows, String headerBg, String altBg) {
        sb.append("<table><tr><th>")
          .append(escapeHtml(NbBundle.getMessage(TokenUsageDialog.class, titleKey)))
          .append("</th>");
        for (String key : headerKeys) {
            sb.append("<th style='text-align:right;'>")
              .append(escapeHtml(NbBundle.getMessage(TokenUsageDialog.class, key)))
              .append("</th>");
        }
        sb.append("</tr>");
        boolean alt = false;
        for (GroupTotals row : rows) {
            String label = row.groupKey() == null || row.groupKey().isBlank()
                    ? NbBundle.getMessage(TokenUsageDialog.class, "LBL_NotAttributed")
                    : row.groupKey();
            sb.append("<tr").append(alt ? " style='background-color: " + altBg + ";'" : "").append(">")
              .append("<td style='white-space:nowrap;'>").append(escapeHtml(label)).append("</td>")
              .append("<td style='text-align:right;'>").append(escapeHtml(
                      UsageStatsFormat.thousands(row.sessions()))).append("</td>")
              .append("<td style='text-align:right;'>").append(escapeHtml(
                      UsageStatsFormat.thousands(row.messages()))).append("</td>")
              .append("<td style='text-align:right;'>").append(escapeHtml(
                      UsageStatsFormat.thousands(row.toolCalls()))).append("</td>")
              .append("<td style='text-align:right;'>").append(escapeHtml(
                      UsageStatsFormat.abbreviated(row.inputTokens()))).append("</td>")
              .append("<td style='text-align:right;'>").append(escapeHtml(
                      UsageStatsFormat.abbreviated(row.outputTokens()))).append("</td>")
              .append("<td style='text-align:right;'>").append(escapeHtml(
                      UsageStatsFormat.abbreviated(row.cachedReadTokens()))).append("</td>")
              .append("<td style='text-align:right;'>").append(escapeHtml(
                      UsageStatsFormat.cost(row.cost()))).append("</td>")
              .append("</tr>");
            alt = !alt;
        }
        sb.append("</table>");
    }

    private static void appendTable(StringBuilder sb, String titleKey, String headerBg,
                                    String altBg, String[][] rows) {
        sb.append("<table><tr><th colspan='2'>")
          .append(escapeHtml(NbBundle.getMessage(TokenUsageDialog.class, titleKey)))
          .append("</th></tr>");
        boolean alt = false;
        for (String[] row : rows) {
            boolean isTotal = "LBL_TotalCost".equals(row[0]);
            String style;
            if (isTotal && alt) {
                style = "font-weight:bold;background-color: " + altBg + ";";
            } else if (isTotal) {
                style = "font-weight:bold;";
            } else if (alt) {
                style = "background-color: " + altBg + ";";
            } else {
                style = "";
            }
            String trAttr = style.isEmpty() ? "" : " style='" + style + "'";
            sb.append("<tr").append(trAttr).append(">")
              .append("<td style='white-space:nowrap;'>")
              .append(escapeHtml(NbBundle.getMessage(TokenUsageDialog.class, row[0]))).append("</td>")
              .append("<td style='text-align:right;'>").append(escapeHtml(row[1])).append("</td>")
              .append("</tr>");
            alt = !alt;
        }
        sb.append("</table>");
    }

    /** Builds a simple placeholder HTML message for the stats pane. */
    private static String buildPlaceholderHtml(ColorTheme theme) {
        return buildPlaceholderHtml(theme, NbBundle.getMessage(TokenUsageDialog.class, "LBL_PressRefreshStats"));
    }

    private static String buildPlaceholderHtml(ColorTheme theme, String message) {
        String fg = theme.toHtmlHex(theme.foreground());
        return "<html><body style='margin:0;padding:8px;color:" + fg
            + ";font-family:sans-serif;font-size:13px;'>"
            + escapeHtml(message) + "</body></html>";
    }

    public static void show(Frame parent) {
        SwingUtilities.invokeLater(() -> {
            TokenUsageDialog dlg = new TokenUsageDialog(parent);
            // Auto-refresh 1s after show so UI paints first
            dlg.autoRefreshTimer = new Timer(1000, e -> dlg.onRefresh(null));
            dlg.autoRefreshTimer.setRepeats(false);
            dlg.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) {
                    if (dlg.autoRefreshTimer != null) dlg.autoRefreshTimer.stop();
                }
            });
            dlg.autoRefreshTimer.start();
            dlg.setVisible(true);
        });
    }
}
