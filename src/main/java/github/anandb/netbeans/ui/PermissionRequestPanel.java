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
import javax.swing.SwingConstants;
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
            BorderFactory.createLineBorder(Color.RED, 2),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        // Row 1: icon + prompt message (full width, no truncation)
        JPanel messageRow = new JPanel(new BorderLayout(8, 0));
        messageRow.setOpaque(false);
        JLabel iconLabel = new JLabel(ThemeManager.getIcon("shield.svg", 18));
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        messageRow.add(iconLabel, BorderLayout.WEST);

        promptLabel = new JLabel(" ");
        promptLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
        promptLabel.setForeground(theme.permissionTitle());
        messageRow.add(promptLabel, BorderLayout.CENTER);
        content.add(messageRow);

        // Row 1b: code/diff content blocks (shown when toolCall has diff data)
        contentBlocks = new JPanel();
        contentBlocks.setLayout(new BoxLayout(contentBlocks, BoxLayout.Y_AXIS));
        contentBlocks.setOpaque(false);
        contentScroll = new JScrollPane(contentBlocks);
        contentScroll.setBorder(BorderFactory.createEmptyBorder());
        contentScroll.setOpaque(false);
        contentScroll.getViewport().setOpaque(false);
        contentScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        contentScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
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
        startWobble();
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

        // Show Diff button (opens multi-file diff TopComponent like Git Changes)
        if (currentFileChanges != null && !currentFileChanges.isEmpty()) {
            JButton showDiffBtn = new JButton("Show Diff");
            showDiffBtn.setFocusPainted(false);
            showDiffBtn.addActionListener(e -> openDiffView(currentFileChanges));
            buttonPanel.add(showDiffBtn);
        }

        if (options != null && options.isArray() && options.size() > 0) {
            for (JsonNode opt : options) {
                String optionId = opt.has("optionId") ? opt.get("optionId").asText() : "";
                String name = opt.has("name") ? opt.get("name").asText() : optionId;
                String kind = opt.has("kind") ? opt.get("kind").asText() : "";

                JButton btn = new JButton(name);
                btn.setFocusPainted(false);
                if (kind.contains("allow")) {
                    allowAction = () -> {
                        pendingResponse.complete(optionId);
                        slideClose();
                        fireResult(name, true);
                    };
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
            JButton denyBtn = new JButton(NbBundle.getMessage(ChatThreadPanel.class, "BTN_Deny"));
            denyBtn.setFocusPainted(false);
            JButton allowBtn = new JButton(NbBundle.getMessage(ChatThreadPanel.class, "BTN_Allow"));
            allowBtn.setFocusPainted(false);
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

    private void buildContentBlocks(JsonNode toolCall) {
        contentBlocks.removeAll();
        currentFileChanges = null;
        if (toolCall == null) return;

        List<FileChange> fragmentChanges = ToolCallDiffParser.parse(toolCall);
        if (fragmentChanges.isEmpty()) return;

        // Dedup — the parser can return the same change from args.oldString
        // and content[] blocks.
        java.util.LinkedHashSet<FileChange> deduped = new java.util.LinkedHashSet<>(fragmentChanges);
        if (deduped.size() < fragmentChanges.size()) {
            fragmentChanges = new ArrayList<>(deduped);
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

        currentFileChanges = expanded;

        // Count occurrences per file path to detect duplicates
        Map<String, Integer> pathCount = new HashMap<>();
        for (FileChange fc : expanded) {
            pathCount.merge(fc.filePath(), 1, Integer::sum);
        }

        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color labelFg = theme.permissionTitle();
        Font monoFont = ThemeManager.getFont().deriveFont(Font.PLAIN);

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

            JLabel nameLabel = new JLabel(labelText);
            nameLabel.setFont(monoFont);
            nameLabel.setForeground(labelFg);
            nameLabel.setIcon(ThemeManager.getIcon("file.svg", 14));
            nameLabel.setIconTextGap(6);
            nameLabel.setToolTipText(fc.filePath());

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
        JButton continueBtn = new JButton("Continue");
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
        startWobble();
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

    boolean isRequestActive() {
        return requestActive;
    }
}
