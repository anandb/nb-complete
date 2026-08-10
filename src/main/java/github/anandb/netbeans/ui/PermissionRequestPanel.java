package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ToolCallDiffParser;
import github.anandb.netbeans.support.ToolCallDiffParser.FileChange;
import github.anandb.netbeans.support.ToolContextExtractor;
import github.anandb.netbeans.support.WobbleAnimator;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

// DSL-LEAF: fixed permission request panel below session dropdown, slides open/closed.
@NbBundle.Messages({
    "BTN_Continue=Continue"
})
final class PermissionRequestPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.from(PermissionRequestPanel.class);

    private static final int SLIDE_STEPS = 8;
    private static final int SLIDE_INTERVAL_MS = 20;

    private final FitEditorPane promptLabel;
    private final JPanel buttonPanel;
    private final JPanel contentBlocks;
    private final JScrollPane contentScroll;
    private final JPanel content;
    private CompletableFuture<String> pendingResponse;
    private List<FileChange> currentFileChanges;
    private CompletableFuture<List<FileChange>> fileChangesFuture;
    private Runnable allowAction;
    private boolean requestActive = false;

    // Wobble animation state
    private final WobbleAnimator wobbleAnimator = new WobbleAnimator(this);

    /** Called when a permission result is ready — receives (statusText, allowed). */
    private BiConsumer<String, Boolean> onResult;

    PermissionRequestPanel() {
        setLayout(new BorderLayout());
        setVisible(false);

        ColorTheme theme = ThemeManager.getCurrentTheme();
        content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(true);
        content.setBackground(theme.permissionBg());
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, theme.permissionAccent()),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.isDark()
                        ? new Color(0x2E3646) : new Color(0xE2E8F0), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
            )
        ));

        // Row 1: icon + prompt message (full width, no truncation)
        JPanel messageRow = new JPanel(new BorderLayout(8, 0)) {
            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }
        };
        messageRow.setOpaque(false);
        JLabel iconLabel = new JLabel(ThemeManager.getIcon("shield.svg", 18));
        iconLabel.setAlignmentY(TOP_ALIGNMENT);
        messageRow.add(iconLabel, BorderLayout.WEST);

        promptLabel = FitEditorPane.createHtmlPane(" ", null, "system", false);
        promptLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        promptLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
        promptLabel.setForeground(theme.permissionTitle());
        promptLabel.setAlignmentY(TOP_ALIGNMENT);
        
        JPanel promptWrapper = new JPanel();
        promptWrapper.setLayout(new BoxLayout(promptWrapper, BoxLayout.Y_AXIS));
        promptWrapper.setOpaque(false);
        promptWrapper.add(promptLabel);
        messageRow.add(promptWrapper, BorderLayout.CENTER);
        content.add(messageRow);

        // Row 1b: code/diff content blocks (shown when toolCall has diff data)
        contentBlocks = new ScrollableBlocksPanel();
        contentBlocks.setLayout(new BoxLayout(contentBlocks, BoxLayout.Y_AXIS));
        contentBlocks.setOpaque(false);
        contentScroll = new JScrollPane(contentBlocks);
        contentScroll.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        contentScroll.setOpaque(false);
        contentScroll.getViewport().setOpaque(false);
        contentScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        content.add(contentScroll);

        // Row 2: buttons right-aligned
        content.add(Box.createVerticalStrut(8));
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        content.add(buttonPanel);

        add(content, BorderLayout.CENTER);
    }

    void setOnResult(BiConsumer<String, Boolean> onResult) {
        this.onResult = onResult;
    }

    List<FileChange> getCurrentFileChanges() {
        return currentFileChanges;
    }

    /** Returns the async future that computes the file changes, if one is in flight. */
    CompletableFuture<List<FileChange>> getFileChangesFuture() {
        return fileChangesFuture;
    }

    void showRequest(String prompt, JsonNode options, CompletableFuture<String> responseFuture) {
        showRequest(prompt, options, responseFuture, null);
    }

    void showRequest(String prompt, JsonNode options, CompletableFuture<String> responseFuture, JsonNode toolCall) {
        this.pendingResponse = responseFuture;
        this.requestActive = true;

        JPanel promptWrapper = (JPanel) promptLabel.getParent();
        while (promptWrapper.getComponentCount() > 1) {
            promptWrapper.remove(1); // remove old context label
        }

        String context = toolCall != null ? 
            ToolContextExtractor.extractToolContext(toolCall, Integer.MAX_VALUE) : null;
        String splitToken = "\n<b>Context:</b> <font face=\"monospace\" color=\"#F44336\"></font>\n";
        
        if (context != null && !context.isEmpty() && prompt.contains(splitToken)) {
            // Split the prompt to inject the context label
            String[] parts = prompt.split(Pattern.quote(splitToken));
            promptLabel.setText("<html>" + parts[0].replace("\n", "<br>") + "</html>");
            
            JPanel contextRow = new JPanel(new BorderLayout(4, 0));
            contextRow.setOpaque(false);
            contextRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            JLabel prefixLabel = new JLabel("<html><b>Context:</b> </html>");
            prefixLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
            prefixLabel.setForeground(ThemeManager.getCurrentTheme().permissionTitle());
            contextRow.add(prefixLabel, BorderLayout.WEST);
            
            StartTruncatingLabel contextLabel = new StartTruncatingLabel(context);
            Font baseFont = ThemeManager.getFont().deriveFont(Font.PLAIN);
            contextLabel.setFont(new Font(Font.MONOSPACED, baseFont.getStyle(), baseFont.getSize()));
            contextLabel.setForeground(new Color(0xF44336));
            contextRow.add(contextLabel, BorderLayout.CENTER);
            
            promptWrapper.add(contextRow);
            
            if (parts.length > 1) {
                String suffixHtml = "<html>" + parts[1].replace("\n", "<br>") + "</html>";
                FitEditorPane suffixLabel = FitEditorPane.createHtmlPane(suffixHtml, null, "system", false);
                suffixLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
                suffixLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
                suffixLabel.setForeground(ThemeManager.getCurrentTheme().permissionTitle());
                promptWrapper.add(suffixLabel);
            }
        } else {
            promptLabel.setText("<html>" + prompt.replace("\n", "<br>") + "</html>");
        }

        contentBlocks.removeAll();
        currentFileChanges = null;
        fileChangesFuture = null;

        // Cap scroll height at 80% of the container height to avoid vertical scrolling if possible
        int parentHeight = getParent() != null && getParent().getHeight() > 0 ? getParent().getHeight() : 600;
        int maxH = (int) (parentHeight * 0.8);
        contentScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(maxH, 150)));

        // Build buttons without Show Diff initially — file changes load async
        buttonPanel.removeAll();
        buildButtons(options, responseFuture);
        buttonPanel.revalidate();
        buttonPanel.repaint();

        // Show the panel immediately so the user sees the prompt + buttons
        slideOpen();
        wobbleAnimator.scheduleStart();

        // Load content blocks asynchronously to avoid blocking EDT with file I/O
        if (toolCall != null) {
            boolean isExecute = toolCall.has("kind") && "execute".equals(toolCall.get("kind").asText());
            if (isExecute) {
                // No file I/O needed — safe to build on EDT
                buildExecuteContext(toolCall);
                contentScroll.revalidate();
            } else {
                loadContentBlocksAsync(toolCall, options, responseFuture);
            }
        }
    }

    /**
     * Loads file-change content blocks on a background thread (file I/O),
     * then populates the UI on EDT. This prevents blocking the EDT when the
     * permission request involves many or large file diffs.
     */
    private void loadContentBlocksAsync(JsonNode toolCall, JsonNode options,
            CompletableFuture<String> responseFuture) {
        CompletableFuture<List<FileChange>> prepareFuture =
            CompletableFuture.supplyAsync(() -> prepareFileChanges(toolCall));
        fileChangesFuture = prepareFuture;
        prepareFuture.thenAcceptAsync(expanded -> {
                if (!requestActive) return; // panel was dismissed while loading
                currentFileChanges = expanded;
                if (expanded.isEmpty()) {
                    showUnparseableDiffMessage();
                } else {
                    populateFileListUI(expanded);
                }
                // Rebuild buttons to show/hide Show Diff button now that
                // currentFileChanges is populated
                buttonPanel.removeAll();
                buildButtons(options, responseFuture);
                buttonPanel.revalidate();
                buttonPanel.repaint();
                contentScroll.revalidate();
                contentScroll.repaint();
            }, SwingUtilities::invokeLater)
            .exceptionally(ex -> {
                LOG.warn("Failed to load content blocks for diff viewer", ex);
                SwingUtilities.invokeLater(() -> {
                    if (!requestActive) return;
                    showUnparseableDiffMessage();
                    contentScroll.revalidate();
                });
                return null;
            });
    }

    /**
     * Rejects the pending permission request, closes the panel, and re-enables input.
     * Called externally (e.g. from Stop button) to dismiss a stuck permission request.
     */
    void rejectRequest() {
        if (requestActive && pendingResponse != null && !pendingResponse.isDone()) {
            pendingResponse.complete("reject");
            slideClose();
            fireResult(NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionDenied"), false);
        }
    }

    /**
     * Force-dismisses the panel (permission or config-confirm) and re-enables the
     * chat input regardless of state. Used to recover from a server crash so the
     * input text area is never left disabled with a stuck modal panel.
     */
    void dismissActiveRequest() {
        if (requestActive && pendingResponse != null && !pendingResponse.isDone()) {
            pendingResponse.complete("reject");
        }
        requestActive = false;
        stopWobble();
        slideClose();
    }

    private void buildButtons(JsonNode options, CompletableFuture<String> responseFuture) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        allowAction = null;

        // Show Diff button — secondary style, does not dismiss the prompt
        if (currentFileChanges != null && !currentFileChanges.isEmpty()) {
            JButton showDiffBtn = createButton(Bundle.BTN_ShowDiff(), null, null, null);
            showDiffBtn.setMnemonic('D');
            showDiffBtn.addActionListener(e -> {
                stopWobble();
                openDiffView(currentFileChanges);
            });
            buttonPanel.add(showDiffBtn);
        }

        if (options != null && options.isArray() && !options.isEmpty()) {
            for (JsonNode opt : options) {
                PermissionOption po = PermissionOption.fromJson(opt);

                JButton btn;
                if (po.kind().contains("allow")) {
                    btn = createButton(po.name(), theme.permissionGrantFg(),
                            theme.permissionGrantBg(), theme.permissionGrantBorder());
                    btn.setMnemonic('A');
                    if ("Always Allow".equalsIgnoreCase(po.name())) {
                        btn.setToolTipText(NbBundle.getMessage(PermissionRequestPanel.class, "HINT_AlwaysAllow"));
                    }
                    allowAction = () -> {
                        pendingResponse.complete(po.id());
                        slideClose();
                        fireResult(po.name(), true);
                    };
                } else if (po.kind().contains("reject") || po.kind().contains("deny")) {
                    btn = createButton(po.name(), theme.permissionDenyFg(),
                            theme.permissionDenyBg(), theme.permissionDenyBorder());
                    btn.setMnemonic('R');
                } else {
                    btn = createButton(po.name(), null, null, null);
                }
                btn.addActionListener(e -> {
                    pendingResponse.complete(po.id());
                    boolean allowed = po.kind().contains("allow");
                    String statusText = po.name();
                    slideClose();
                    fireResult(statusText, allowed);
                });
                buttonPanel.add(btn);
            }
        } else {
            JButton denyBtn = createButton(
                    NbBundle.getMessage(ChatThreadPanel.class, "BTN_Deny"),
                    theme.permissionDenyFg(), theme.permissionDenyBg(),
                    theme.permissionDenyBorder());
            denyBtn.setMnemonic('R');
            JButton allowBtn = createButton(
                    NbBundle.getMessage(ChatThreadPanel.class, "BTN_Allow"),
                    theme.permissionGrantFg(), theme.permissionGrantBg(),
                    theme.permissionGrantBorder());
            allowBtn.setMnemonic('A');
            allowAction = () -> {
                pendingResponse.complete("allow");
                slideClose();
                fireResult(NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionGranted"), true);
            };

            allowBtn.addActionListener(e -> allowAction.run());

            denyBtn.addActionListener(e -> {
                pendingResponse.complete("reject");
                slideClose();
                fireResult(NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionDenied"), false);
            });

            buttonPanel.add(denyBtn);
            buttonPanel.add(allowBtn);
        }
    }

    /** Creates a styled button. Pass null for any color to use defaults. */
    private static JButton createButton(String text, Color fg, Color bg, Color border) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        if (fg != null) btn.setForeground(fg);
        if (bg != null) btn.setBackground(bg);
        if (border != null) btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        return btn;
    }

    /**
     * Runs on a background thread. Parses diff data from the tool call,
     * reads files from disk to expand fragments into full-file diffs,
     * and deduplicates. No Swing components are created here.
     */
    private List<FileChange> prepareFileChanges(JsonNode toolCall) {
        List<FileChange> fragmentChanges = ToolCallDiffParser.parse(toolCall);
        if (fragmentChanges.isEmpty()) {
            return List.of();
        }

        // Expand each fragment individually (read full file, apply just this
        // fragment's change) without merging. A file with 3 edits produces 3
        // entries, each showing full-file diff with that one hunk applied.
        String cwd = getSessionDirectory();
        List<FileChange> expanded = new ArrayList<>();
        for (FileChange fc : fragmentChanges) {
            if (fc.status() == 'M' && !"unknown".equals(fc.filePath())
                    && !fc.oldContent().isEmpty()) {
                File f = resolveFilePath(fc.filePath(), cwd);
                if (f != null && f.isFile()) {
                    try {
                        String fullContent = Files.readString(f.toPath());
                        if (fullContent.contains(fc.oldContent())) {
                            String modified = fullContent.replace(fc.oldContent(), fc.newContent());
                            expanded.add(new FileChange(fc.filePath(), fullContent, modified, 'M'));
                            continue;
                        }
                    } catch (IOException e) {
                        // fall through to add fragment as-is
                    }
                }
            }
            expanded.add(fc);
        }

        // Dedup again after expansion — rawInput.diff and content[] can both
        // describe the same change and produce identical expanded files.
        // Ignore trailing newlines when comparing to handle diff parser variations.
        List<FileChange> dedupedExpanded = new ArrayList<>();
        for (FileChange fc : expanded) {
            boolean duplicate = false;
            for (FileChange ex : dedupedExpanded) {
                if (ex.filePath().equals(fc.filePath()) &&
                    ex.status() == fc.status() &&
                    trimSafe(ex.oldContent()).equals(trimSafe(fc.oldContent())) &&
                    trimSafe(ex.newContent()).equals(trimSafe(fc.newContent()))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                dedupedExpanded.add(fc);
            }
        }
        return dedupedExpanded;
    }

    /**
     * Runs on EDT. Builds the file-list UI rows from the pre-computed
     * FileChange list produced by {@link #prepareFileChanges}.
     */
    private void populateFileListUI(List<FileChange> expanded) {
        // Count occurrences per file path to detect duplicates
        Map<String, Integer> pathCount = new HashMap<>();
        for (FileChange fc : expanded) {
            pathCount.merge(fc.filePath(), 1, Integer::sum);
        }

        ColorTheme theme = ThemeManager.getCurrentTheme();
        Font monoFont = IconResourceManager.getMonospaceFont();

        // Track per-file hunk index
        Map<String, Integer> hunkIndex = new HashMap<>();

        for (int i = 0; i < expanded.size(); i++) {
            FileChange fc = expanded.get(i);
            if (i > 0) contentBlocks.add(Box.createVerticalStrut(4));

            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
            row.setOpaque(false);

            String dispPath = displayPath(fc.filePath());
            String name = new File(fc.filePath()).getName();
            int count = pathCount.getOrDefault(fc.filePath(), 1);
            if (count > 1) {
                int n = hunkIndex.merge(fc.filePath(), 1, Integer::sum);
                name = name + " (H" + n + ")";
            }

            // Show filename prominently, parent path greyed out
            JLabel nameLabel = new JLabel();
            nameLabel.setIcon(ThemeManager.getIcon("file.svg", 14));
            nameLabel.setIconTextGap(6);
            nameLabel.setToolTipText(fc.filePath());

            String parent = dispPath.substring(0, Math.max(0, dispPath.length() - new File(fc.filePath()).getName().length()));
            String fnameHex = Integer.toHexString(theme.permissionFilename().getRGB() & 0xFFFFFF);
            String pathHex = Integer.toHexString(theme.permissionPath().getRGB() & 0xFFFFFF);
            if (!parent.isEmpty() && !parent.equals(new File(fc.filePath()).getName())) {
                nameLabel.setText("<html><font color='#" + fnameHex
                        + "'><b>" + escapeHtml(name) + "</b></font> <font color='#"
                        + pathHex + "'>" + escapeHtml(parent) + "</font></html>");
            } else {
                nameLabel.setText("<html><font color='#" + fnameHex
                        + "'><b>" + escapeHtml(name) + "</b></font></html>");
            }

            String statusStr = "(" + fc.status() + ")";
            JLabel statusLabel = new JLabel(statusStr);
            statusLabel.setFont(monoFont);
            statusLabel.setForeground(statusColor(fc.status()));

            row.add(nameLabel, BorderLayout.CENTER);
            row.add(statusLabel, BorderLayout.EAST);
            contentBlocks.add(row);
        }
    }

    private void showUnparseableDiffMessage() {
        JLabel msgLabel = new JLabel("Unable to parse diff preview.");
        msgLabel.setForeground(ThemeManager.getCurrentTheme().mutedForeground());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setOpaque(false);
        row.add(msgLabel);
        contentBlocks.add(row);
    }



    /** Builds context for execute (bash) tool calls: shows command + workdir. */
    private void buildExecuteContext(JsonNode toolCall) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        Font monoFont = IconResourceManager.getMonospaceFont();
        String command = null;
        String workdir = null;

        // Extract from rawInput (ACP permission format)
        if (toolCall.has("rawInput") && toolCall.get("rawInput").isObject()) {
            JsonNode rawInput = toolCall.get("rawInput");
            if (rawInput.has("command")) command = rawInput.get("command").asText();
            if (rawInput.has("workdir")) workdir = rawInput.get("workdir").asText();
        }
        // Fallback to args
        if (command == null && (toolCall.has("args") || toolCall.has("arguments"))) {
            JsonNode args = toolCall.has("args") ? toolCall.get("args") : toolCall.get("arguments");
            if (args.has("command")) command = args.get("command").asText();
            if (args.has("workdir")) workdir = args.get("workdir").asText();
        }

        if (command == null) return;

        // Command row
        JPanel cmdRow = new JPanel(new BorderLayout(4, 0));
        cmdRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        cmdRow.setOpaque(false);
        JLabel cmdIcon = new JLabel(ThemeManager.getIcon("tool.svg", 14));
        cmdIcon.setVerticalAlignment(SwingConstants.TOP);
        
        JTextArea cmdText = new JTextArea(command);
        cmdText.setEditable(false);
        cmdText.setLineWrap(true);
        cmdText.setWrapStyleWord(true);
        cmdText.setFont(monoFont);
        cmdText.setForeground(theme.permissionFilename());
        cmdText.setOpaque(false);
        cmdText.setBackground(new Color(0, 0, 0, 0));
        cmdText.setBorder(null);
        cmdText.setToolTipText(command);
        
        cmdRow.add(cmdIcon, BorderLayout.WEST);
        cmdRow.add(cmdText, BorderLayout.CENTER);
        contentBlocks.add(cmdRow);

        // Workdir row (if available)
        if (workdir != null && !workdir.isEmpty()) {
            JPanel wdRow = new JPanel(new BorderLayout(4, 0));
            wdRow.setOpaque(false);
            // Indent with empty label of icon width
            JLabel wdIcon = new JLabel(ThemeManager.getIcon("file.svg", 14));
            wdIcon.setVerticalAlignment(SwingConstants.TOP);
            
            String dispWd = displayPath(workdir);
            JTextArea wdText = new JTextArea(dispWd);
            wdText.setEditable(false);
            wdText.setLineWrap(true);
            wdText.setWrapStyleWord(true);
            wdText.setFont(monoFont);
            wdText.setForeground(theme.permissionPath());
            wdText.setOpaque(false);
            wdText.setBackground(new Color(0, 0, 0, 0));
            wdText.setBorder(null);
            wdText.setToolTipText(workdir);
            
            wdRow.add(wdIcon, BorderLayout.WEST);
            wdRow.add(wdText, BorderLayout.CENTER);
            contentBlocks.add(wdRow);
        }
    }

    /** Strips the session working directory prefix from a file path for display. */
    static String displayPath(String filePath) {
        String cwd = getSessionDirectory();
        if (cwd != null && filePath.startsWith(cwd + "/")) {
            return filePath.substring(cwd.length() + 1);
        }
        return new File(filePath).getName();
    }

    /** Returns the current session's working directory, or null. */
    private static String getSessionDirectory() {
        SessionQuery sq = Lookup.getDefault().lookup(SessionQuery.class);
        return sq != null ? sq.getCurrentSessionDirectory() : null;
    }

    /** Resolves a possibly-relative file path against the session directory. */
    private static File resolveFilePath(String path, String cwd) {
        if (path.startsWith("/")) return new File(path);
        if (cwd != null) return new File(cwd, path);
        return new File(path);
    }

    private static String trimSafe(String s) {
        if (s == null) return "";
        return s.trim();
    }

    /** Flashes the OS taskbar to draw attention to the permission request. */
    private static void flashTaskbar() {
        try {
            if (!Taskbar.isTaskbarSupported()) return;
            Window w = WindowManager.getDefault().getMainWindow();
            if (w == null) return;
            Taskbar tb = Taskbar.getTaskbar();
            // requestWindowUserAttention (Java 20+) or requestWindowFocus (Java 9-17)
            try {
                tb.getClass().getMethod("requestWindowUserAttention", Window.class)
                        .invoke(tb, w);
            } catch (NoSuchMethodException e) {
                try {
                    tb.getClass().getMethod("requestWindowFocus", Window.class)
                            .invoke(tb, w);
                } catch (NoSuchMethodException e2) {
                    // Neither method available — skip
                }
            }
        } catch (Exception ex) {
            // Silently ignore — taskbar flash is best-effort
        }
    }

    private static Color statusColor(char status) {
        return switch (status) {
            case 'A' -> new Color(0x28a745);
            case 'D' -> new Color(0xd73a49);
            default -> new Color(0x0366d6);
        };
    }

    /** Opens a TopComponent with file list + side-by-side diff (delegates to StashDiffAction). */
    static void openDiffView(List<FileChange> changes) {
        StashDiffAction.openPermissionDiffView(changes);
    }

    private void fireResult(String statusText, boolean allowed) {
        requestActive = false;
        if (onResult != null) {
            onResult.accept(statusText, allowed);
        }
    }

    private void slideOpen() {
        setVisible(true);
        // Clear any stale preferred-size override from previous slideClose()
        // so getPreferredSize() returns the natural layout height.
        setPreferredSize(null);

        int expectedWidth = getParent() != null ? getParent().getWidth() : 400;
        if (expectedWidth > 0) {
            setSize(new Dimension(expectedWidth, Short.MAX_VALUE));
            doLayout();
        }

        revalidate();
        int targetHeight = getPreferredSize().height;
        setPreferredSize(new Dimension(expectedWidth, 0));
        revalidate();

        flashTaskbar();

        // Enter = Allow, Escape = Reject
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && allowAction != null) {
                    e.consume();
                    allowAction.run();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    triggerReject();
                }
            }
        });
        setFocusable(true);
        requestFocusInWindow();

        Timer timer = new Timer(SLIDE_INTERVAL_MS, null);
        final int[] step = {0};
        timer.addActionListener(e -> {
            step[0]++;
            int h = targetHeight * step[0] / SLIDE_STEPS;
            setPreferredSize(new Dimension(getParent() != null ? getParent().getWidth() : 400, h));
            setSize(new Dimension(getWidth(), h));
            revalidate();
            if (step[0] >= SLIDE_STEPS) {
                timer.stop();
                // Release preferred-size override so layout uses natural height
                setPreferredSize(null);
                revalidate();
            }
        });
        timer.start();
    }

    void slideClose() {
        if (!isVisible()) return;
        stopWobble();
        int startHeight = getHeight();
        Timer timer = new Timer(SLIDE_INTERVAL_MS, null);
        final int[] step = {SLIDE_STEPS};
        timer.addActionListener(e -> {
            step[0]--;
            int h = startHeight * step[0] / SLIDE_STEPS;
            setPreferredSize(new Dimension(getParent() != null ? getParent().getWidth() : 400, h));
            revalidate();
            if (step[0] <= 0) {
                timer.stop();
                setVisible(false);
                setPreferredSize(new Dimension(0, 0));
                revalidate();
            }
        });
        timer.start();
    }

    /**
     * Shows a config-confirmation prompt with a single Continue button.
     * The future is completed when the user clicks Continue. No permission
     * result is fired (no chat message added), making this suitable for
     * the pre-preamble config flow.
     */
    void showConfigConfirm(String prompt, CompletableFuture<String> onContinue) {
        this.pendingResponse = onContinue;
        this.requestActive = true;

        promptLabel.setText("<html>" + prompt.replace("\n", "<br>") + "</html>");

        buttonPanel.removeAll();
        JButton continueBtn = new JButton(Bundle.BTN_Continue());
        continueBtn.setFocusPainted(false);
        continueBtn.addActionListener(e -> {
            pendingResponse.complete("continue");
            requestActive = false;
            slideClose();
        });
        buttonPanel.add(continueBtn);
        buttonPanel.revalidate();
        buttonPanel.repaint();

        slideOpen();
        wobbleAnimator.scheduleStart();
    }

    @Override
    public void paint(Graphics g) {
        if (wobbleAnimator.x() != 0 || wobbleAnimator.y() != 0) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.translate(wobbleAnimator.x(), wobbleAnimator.y());
            super.paint(g2d);
            g2d.dispose();
        } else {
            super.paint(g);
        }
    }

    @Override
    public void removeNotify() {
        wobbleAnimator.stop();
        super.removeNotify();
    }

    /** Stops the wobble animation and its repeat cycle. */
    private void stopWobble() {
        wobbleAnimator.stop();
    }

    /** Triggers the "allow" action, e.g. from a global keyboard shortcut. */
    void triggerAllow() {
        if (requestActive && isVisible() && allowAction != null) {
            allowAction.run();
        }
    }

    /** Triggers the "reject" action, e.g. from Escape key. */
    void triggerReject() {
        if (requestActive && isVisible()) {
            pendingResponse.complete("reject");
            slideClose();
            fireResult(NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionDenied"), false);
        }
    }

    boolean isRequestActive() {
        return requestActive;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static class ScrollableBlocksPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 16;
        }
        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return 16;
        }
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
