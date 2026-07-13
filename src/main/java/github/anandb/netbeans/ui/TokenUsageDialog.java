package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openide.util.Lookup;

import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.Logger;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

// DSL-LEAF: a standalone dialog for token usage stats.
// Built imperatively — no need for the full SwingTree DSL.
public class TokenUsageDialog extends JDialog {

    private static final Logger LOG = Logger.from(TokenUsageDialog.class);
    private static final long serialVersionUID = 1L;

    private static final String PROJECT_CURRENT = "Current Project";
    private static final String PROJECT_ALL = "All";
    private static final String[] PROJECT_OPTIONS = { PROJECT_CURRENT, PROJECT_ALL };
    private static final String ALL_MODELS = "All Models";
    private static final java.util.regex.Pattern ANSI_ESCAPE =
        java.util.regex.Pattern.compile("\u001b\\[[0-9;]*[a-zA-Z]");

    private final JSpinner daysSpinner;
    private final JComboBox<String> modelCombo;
    private final JComboBox<String> projectCombo;
    private final JTextArea statsArea;
    private final JButton refreshBtn;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private javax.swing.Timer autoRefreshTimer;
    private boolean firstRefresh = true;

    public TokenUsageDialog(java.awt.Frame owner) {
        super(owner, "Token Stats", true);
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

        // --- Form ---
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.X_AXIS));
        formPanel.setOpaque(false);

        // Days
        daysSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 7, 1));
        daysSpinner.setPreferredSize(new Dimension(60, 28));
        daysSpinner.setMaximumSize(new Dimension(60, 28));

        JLabel daysLabel = new JLabel("Days:");
        daysLabel.setLabelFor(daysSpinner);
        formPanel.add(daysLabel);
        formPanel.add(Box.createHorizontalStrut(6));
        formPanel.add(daysSpinner);
        formPanel.add(Box.createHorizontalStrut(12));

        // Model
        modelCombo = new JComboBox<>(new String[]{ ALL_MODELS });
        loadModelListAsync();
        modelCombo.setPreferredSize(new Dimension(180, 28));
        modelCombo.setMaximumSize(new Dimension(180, 28));

        JLabel modelLabel = new JLabel("Model:");
        modelLabel.setLabelFor(modelCombo);
        formPanel.add(modelLabel);
        formPanel.add(Box.createHorizontalStrut(6));
        formPanel.add(modelCombo);
        formPanel.add(Box.createHorizontalStrut(12));

        // Project
        projectCombo = new JComboBox<>(PROJECT_OPTIONS);
        projectCombo.setPreferredSize(new Dimension(140, 28));
        projectCombo.setMaximumSize(new Dimension(140, 28));

        JLabel projectLabel = new JLabel("Project:");
        projectLabel.setLabelFor(projectCombo);
        formPanel.add(projectLabel);
        formPanel.add(Box.createHorizontalStrut(6));
        formPanel.add(projectCombo);

        // Refresh button to the right of project dropdown
        formPanel.add(Box.createHorizontalGlue());
        refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(this::onRefresh);
        formPanel.add(refreshBtn);

        content.add(formPanel, BorderLayout.BEFORE_FIRST_LINE);

        // --- Stats area ---
        statsArea = new JTextArea();
        statsArea.setEditable(false);
        statsArea.setFont(ThemeManager.getMonospaceFont());
        statsArea.setBackground(theme.codeBackground());
        statsArea.setForeground(theme.codeForeground());
        statsArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.tableBorder(), 1),
            new EmptyBorder(8, 8, 8, 8)
        ));
        statsArea.setText("Press Refresh to fetch token usage stats.");
        addContextMenu(statsArea);

        JScrollPane scrollPane = new JScrollPane(statsArea);
        scrollPane.setPreferredSize(new Dimension(500, 260));
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

    private void onRefresh(ActionEvent e) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        int days = (int) daysSpinner.getValue();
        String model = (String) modelCombo.getSelectedItem();
        String project = (String) projectCombo.getSelectedItem();
        refreshBtn.setEnabled(false);
        statsArea.setText("Fetching stats...");
        statsArea.setCaretPosition(0);

        new Thread(() -> {
            try {
                String projectDir = null;
                if (PROJECT_CURRENT.equals(project)) {
                    SessionQuery sq = Lookup.getDefault().lookup(SessionQuery.class);
                    if (sq != null) {
                        projectDir = sq.getCurrentSessionDirectory();
                    }
                }
                String result = ANSI_ESCAPE.matcher(runStatsCommand(days, model, projectDir)).replaceAll("");
                SwingUtilities.invokeLater(() -> {
                    statsArea.setText(result);
                    statsArea.setCaretPosition(0);
                    if (firstRefresh) {
                        firstRefresh = false;
                        autoSizeInitial();
                    }
                });
            } catch (Exception ex) {
                LOG.log(java.util.logging.Level.WARNING, "Failed to fetch token usage stats", ex);
                SwingUtilities.invokeLater(() -> {
                    statsArea.setText("Error: " + ex.getMessage());
                    statsArea.setCaretPosition(0);
                });
            } finally {
                SwingUtilities.invokeLater(() -> refreshBtn.setEnabled(true));
                running.set(false);
            }
        }, "token-stats").start();
    }

    private static String runStatsCommand(int days, String model, String projectDir) throws Exception {
        String binary = BinaryResolver.resolveExecutablePath();
        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        cmd.add("stats");
        cmd.add("--days");
        cmd.add(String.valueOf(days));
        // Always pass --models; empty value for "All Models"
        cmd.add("--models");
        if (!ALL_MODELS.equals(model)) {
            cmd.add(model);
        }
        if (projectDir != null) {
            cmd.add("--project");
            cmd.add("");
        }

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

    private static void addContextMenu(JTextArea area) {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> {
            String sel = area.getSelectedText();
            if (sel == null || sel.isEmpty()) {
                sel = area.getText();
            }
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(sel), null);
        });
        popup.add(copyItem);
        JMenuItem selectAllItem = new JMenuItem("Select All");
        selectAllItem.addActionListener(e -> area.selectAll());
        popup.add(selectAllItem);

        area.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) popup.show(area, e.getX(), e.getY());
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) popup.show(area, e.getX(), e.getY());
            }
        });
    }

    /** Height = 90% of parent, width = longest line + padding. */
    private void autoSizeInitial() {
        java.awt.Window parent = SwingUtilities.getWindowAncestor(this);
        int h = parent != null ? (int)(parent.getHeight() * 0.9) : 520;

        // Width based on longest line
        String text = statsArea.getText();
        java.awt.FontMetrics fm = statsArea.getFontMetrics(statsArea.getFont());
        int maxW = 400;
        if (text != null) {
            for (String line : text.split("\n")) {
                int w = fm.stringWidth(line) + 120;
                if (w > maxW) maxW = w;
            }
        }
        setSize(new Dimension(Math.min(maxW, 900), h));
        revalidate();
    }

    /** Fetches model list via `opencode models` in background, updates combo on EDT. */
    private void loadModelListAsync() {
        new Thread(() -> {
            try {
                String binary = BinaryResolver.resolveExecutablePath();
                ProcessBuilder pb = new ProcessBuilder(binary, "models");
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line).append('\n');
                }
                proc.waitFor();
                String[] lines = sb.toString().trim().split("\n");
                if (lines.length > 0 && !lines[0].isEmpty()) {
                    java.util.Set<String> models = new java.util.LinkedHashSet<>();
                    models.add(ALL_MODELS);
                    for (String l : lines) {
                        String m = l.trim();
                        if (!m.isEmpty()) {
                            // Strip provider prefix — keep only model name after last /
                            int slash = m.lastIndexOf('/');
                            if (slash >= 0) m = m.substring(slash + 1);
                            models.add(m);
                        }
                    }
                    SwingUtilities.invokeLater(() -> {
                        modelCombo.removeAllItems();
                        for (String m : models) modelCombo.addItem(m);
                    });
                }
            } catch (Exception ex) {
                LOG.log(java.util.logging.Level.FINE, "Failed to load model list", ex);
            }
        }, "load-models").start();
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
                }
            });
            dlg.autoRefreshTimer.start();
            dlg.setVisible(true);
        });
    }
}
