package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ToolCallDiffParser;
import github.anandb.netbeans.support.ToolCallDiffParser.FileChange;
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

    private final JLabel promptLabel;
    private final JPanel buttonPanel;
    private final JPanel contentBlocks;
    private final JScrollPane contentScroll;
    private final JPanel content;
    private CompletableFuture<String> pendingResponse;
    private List<FileChange> currentFileChanges;
    private Runnable allowAction;
    private boolean requestActive = false;
    private Consumer<Boolean> inputEnableCallback;

    // Wobble animation state
    private int wobbleX;
    private int wobbleY;
    private Timer wobbleTimer;
    private Timer wobbleRestartTimer;
    private Timer wobbleDelayTimer;

    /** Called when a permission result is ready — receives (statusText, allowed). */
    private java.util.function.BiConsumer<String, Boolean> onResult;

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
        JPanel messageRow = new JPanel(new BorderLayout(8, 0));
        messageRow.setOpaque(false);
        JLabel iconLabel = new JLabel(ThemeManager.getIcon("shield.svg", 18));
        iconLabel.setAlignmentY(TOP_ALIGNMENT);
        messageRow.add(iconLabel, BorderLayout.WEST);

        promptLabel = new JLabel(" ");
        promptLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
        promptLabel.setForeground(theme.permissionTitle());
        promptLabel.setAlignmentY(TOP_ALIGNMENT);
        messageRow.add(promptLabel, BorderLayout.CENTER);
        content.add(messageRow);

        // Row 1b: code/diff content blocks (shown when toolCall has diff data)
        contentBlocks = new JPanel();
        contentBlocks.setLayout(new BoxLayout(contentBlocks, BoxLayout.Y_AXIS));
        contentBlocks.setOpaque(false);
        contentScroll = new JScrollPane(contentBlocks);
        contentScroll.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        contentScroll.setOpaque(false);
        contentScroll.getViewport().setOpaque(false);
        contentScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        contentScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        content.add(contentScroll);

        // Row 2: buttons right-aligned
        content.add(Box.createVerticalStrut(8));
        buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        content.add(buttonPanel);

        add(content, BorderLayout.CENTER);
    }

    void setOnResult(java.util.function.BiConsumer<String, Boolean> onResult) {
        this.onResult = onResult;
    }

    void showRequest(String prompt, JsonNode options, CompletableFuture<String> responseFuture) {
        showRequest(prompt, options, responseFuture, null);
    }

    void showRequest(String prompt, JsonNode options, CompletableFuture<String> responseFuture, JsonNode toolCall) {
        this.pendingResponse = responseFuture;
        this.requestActive = true;

        promptLabel.setText("<html>" + prompt.replace("\n", "<br>") + "</html>");

        buildContentBlocks(toolCall);

        // Cap scroll height at half the container height
        int maxH = getParent() != null ? getParent().getHeight() / 2 : 400;
        contentScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(maxH, 150)));

        buttonPanel.removeAll();
        buildButtons(options, responseFuture);
        buttonPanel.revalidate();
        buttonPanel.repaint();

        slideOpen();
        scheduleWobbleStart();
    }

    /** Sets a callback that enables/disables the chat input area while this panel is active. */
    void setInputEnableCallback(Consumer<Boolean> callback) {
        this.inputEnableCallback = callback;
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

        if (options != null && options.isArray() && options.size() > 0) {
            for (JsonNode opt : options) {
                String optionId = opt.has("optionId") ? opt.get("optionId").asText() : "";
                String name = opt.has("name") ? opt.get("name").asText() : optionId;
                String kind = opt.has("kind") ? opt.get("kind").asText() : "";

                // Skip "always allow" — not clear how to reset it
                if (kind.contains("always") || name.toLowerCase().contains("always")) continue;

                JButton btn;
                if (kind.contains("allow")) {
                    btn = createButton(name, theme.permissionGrantFg(),
                            theme.permissionGrantBg(), theme.permissionGrantBorder());
                    btn.setMnemonic('A');
                    allowAction = () -> {
                        pendingResponse.complete(optionId);
                        slideClose();
                        fireResult(name, true);
                    };
                } else if (kind.contains("reject") || kind.contains("deny")) {
                    btn = createButton(name, theme.permissionDenyFg(),
                            theme.permissionDenyBg(), theme.permissionDenyBorder());
                    btn.setMnemonic('R');
                } else {
                    btn = createButton(name, null, null, null);
                }
                btn.addActionListener(e -> {
                    pendingResponse.complete(optionId);
                    boolean allowed = kind.contains("allow");
                    String statusText = name;
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

    private void buildContentBlocks(JsonNode toolCall) {
        contentBlocks.removeAll();
        currentFileChanges = null;
        if (toolCall == null) return;

        List<FileChange> fragmentChanges = ToolCallDiffParser.parse(toolCall);
        if (fragmentChanges.isEmpty()) return;

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
        java.util.LinkedHashSet<FileChange> postExpandDedup = new java.util.LinkedHashSet<>(expanded);
        if (postExpandDedup.size() < expanded.size()) {
            expanded = new ArrayList<>(postExpandDedup);
        }

        currentFileChanges = expanded;

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

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            row.setOpaque(false);

            String dispPath = displayPath(fc.filePath());
            String labelText;
            int count = pathCount.getOrDefault(fc.filePath(), 1);
            if (count > 1) {
                int n = hunkIndex.merge(fc.filePath(), 1, Integer::sum);
                labelText = dispPath + " : Hunk " + n;
            } else {
                labelText = dispPath;
            }

            // Show filename prominently, parent path greyed out
            JLabel nameLabel = new JLabel();
            nameLabel.setIcon(ThemeManager.getIcon("file.svg", 14));
            nameLabel.setIconTextGap(6);
            nameLabel.setToolTipText(fc.filePath());
            String name = new File(fc.filePath()).getName();
            String parent = dispPath.substring(0, Math.max(0, dispPath.length() - name.length()));
            String fnameHex = Integer.toHexString(theme.permissionFilename().getRGB() & 0xFFFFFF);
            String pathHex = Integer.toHexString(theme.permissionPath().getRGB() & 0xFFFFFF);
            if (!parent.isEmpty() && !parent.equals(name)) {
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

            row.add(nameLabel);
            row.add(statusLabel);
            contentBlocks.add(row);
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
        revalidate();
        int targetHeight = getPreferredSize().height;
        setPreferredSize(new Dimension(getParent() != null ? getParent().getWidth() : 400, 0));
        revalidate();

        // Disable chat input while permission dialog is active
        if (inputEnableCallback != null) inputEnableCallback.accept(false);
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
        // Re-enable chat input immediately
        if (inputEnableCallback != null) inputEnableCallback.accept(true);
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
        scheduleWobbleStart();
    }

    @Override
    public void paint(Graphics g) {
        if (wobbleX != 0 || wobbleY != 0) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.translate(wobbleX, wobbleY);
            super.paint(g2d);
            g2d.dispose();
        } else {
            super.paint(g);
        }
    }

    @Override
    public void removeNotify() {
        stopWobble();
        super.removeNotify();
    }

    /** Schedules wobble to start after 10 seconds of inactivity. */
    private void scheduleWobbleStart() {
        cancelWobbleDelay();
        wobbleDelayTimer = new Timer(10000, e -> {
            wobbleDelayTimer.stop();
            wobbleDelayTimer = null;
            startWobble();
        });
        wobbleDelayTimer.setRepeats(false);
        wobbleDelayTimer.start();
    }

    /** Cancels any pending wobble delay. */
    private void cancelWobbleDelay() {
        if (wobbleDelayTimer != null) {
            wobbleDelayTimer.stop();
            wobbleDelayTimer = null;
        }
    }

    /** Shake the panel side-to-side to attract attention, repeating every few seconds. */
    void startWobble() {
        if (wobbleTimer != null && wobbleTimer.isRunning()) {
            // Already wobbling — just reset the cycle (don't double-start)
            return;
        }

        final int[] yOffsets = {0, -2, -4, -6, -7, -6, -4, -2, 0, -1, -3, -4, -3, -1, 0};
        final int[] xOffsets = {0, -2, 2, -2, 2, -1, 1, 0, 0, 0, 0, 0, 0, 0, 0};

        wobbleTimer = new Timer(30, new ActionListener() {
            private int frame = 0;
            @Override
            public void actionPerformed(ActionEvent e) {
                if (frame < yOffsets.length) {
                    wobbleX = xOffsets[frame];
                    wobbleY = yOffsets[frame];
                    repaint();
                    frame++;
                } else {
                    wobbleX = 0;
                    wobbleY = 0;
                    repaint();
                    wobbleTimer.stop();
                    wobbleTimer = null;
                    // Schedule repeat after 5 seconds
                    wobbleRestartTimer = new Timer(5000, ev -> startWobble());
                    wobbleRestartTimer.setRepeats(false);
                    wobbleRestartTimer.start();
                }
            }
        });
        wobbleTimer.start();
    }

    /** Stops the wobble animation and its repeat cycle. */
    private void stopWobble() {
        cancelWobbleDelay();
        if (wobbleTimer != null) {
            wobbleTimer.stop();
            wobbleTimer = null;
        }
        if (wobbleRestartTimer != null) {
            wobbleRestartTimer.stop();
            wobbleRestartTimer = null;
        }
        wobbleX = 0;
        wobbleY = 0;
        repaint();
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
}
