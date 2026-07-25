package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

// DSL-LEAF: fixed permission request panel below session dropdown, slides open/closed.
final class PermissionRequestPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.from(PermissionRequestPanel.class);

    private static final int SLIDE_STEPS = 8;
    private static final int SLIDE_INTERVAL_MS = 20;

    private final JLabel promptLabel;
    private final JPanel buttonPanel;
    private final JPanel contentBlocks;
    private final JPanel content;
    private CompletableFuture<String> pendingResponse;
    private boolean requestActive = false;

    // Wobble animation state
    private int wobbleX;
    private int wobbleY;
    private Timer wobbleTimer;

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
            BorderFactory.createMatteBorder(0, 0, 1, 0, theme.permissionBorder()),
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
        content.add(contentBlocks);

        // Row 2: buttons right-aligned
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

        buttonPanel.removeAll();
        buildButtons(options, responseFuture);
        buttonPanel.revalidate();
        buttonPanel.repaint();

        slideOpen();
        startWobble();
    }

    private void buildButtons(JsonNode options, CompletableFuture<String> responseFuture) {
        ColorTheme theme = ThemeManager.getCurrentTheme();

        if (options != null && options.isArray() && options.size() > 0) {
            for (JsonNode opt : options) {
                String optionId = opt.has("optionId") ? opt.get("optionId").asText() : "";
                String name = opt.has("name") ? opt.get("name").asText() : optionId;
                String kind = opt.has("kind") ? opt.get("kind").asText() : "";

                JButton btn = new JButton(name);
                btn.setFocusPainted(false);
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

            allowBtn.addActionListener(e -> {
                pendingResponse.complete("allow");
                slideClose();
                fireResult(NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionGranted"), true);
            });

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
        if (toolCall == null) return;

        // 1. Unified diff from rawInput.diff (most common for edit tools)
        if (toolCall.has("rawInput")) {
            JsonNode ri = toolCall.get("rawInput");
            if (ri.has("diff") && isNotBlank(ri.get("diff").asText())) {
                String diffText = ri.get("diff").asText().trim();
                CollapsibleCodePane codePane = new CollapsibleCodePane("diff", diffText, true);
                codePane.setAlignmentX(Component.LEFT_ALIGNMENT);
                contentBlocks.add(Box.createVerticalStrut(8));
                contentBlocks.add(codePane);
            }
        }

        // 2. oldString/newString from arguments (tool call format)
        JsonNode args = toolCall.has("arguments") ? toolCall.get("arguments")
                : toolCall.has("args") ? toolCall.get("args") : null;
        if (args != null && args.has("oldString") && args.has("newString")) {
            String diff = "- " + args.get("oldString").asText() + "\n+ " + args.get("newString").asText();
            CollapsibleCodePane codePane = new CollapsibleCodePane("diff", diff.trim(), true);
            codePane.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentBlocks.add(Box.createVerticalStrut(8));
            contentBlocks.add(codePane);
        }

        // 3. Rich content blocks from toolCall.content[] (PermissionBubble format)
        if (toolCall.has("content") && toolCall.get("content").isArray()) {
            for (JsonNode block : toolCall.get("content")) {
                if (!block.has("type")) continue;
                String type = block.get("type").asText();
                String codeText = null;
                String lang = "text";
                if ("text".equals(type) && block.has("text")) {
                    codeText = block.get("text").asText();
                } else if ("diff".equals(type)) {
                    lang = "diff";
                    if (block.has("text")) {
                        codeText = block.get("text").asText();
                    } else if (block.has("patch")) {
                        codeText = block.get("patch").asText();
                    } else if (block.has("oldText") && block.has("newText")) {
                        codeText = "- " + block.get("oldText").asText() + "\n+ " + block.get("newText").asText();
                    }
                }
                if (isNotBlank(codeText)) {
                    CollapsibleCodePane codePane = new CollapsibleCodePane(lang, codeText.trim(), true);
                    codePane.setAlignmentX(Component.LEFT_ALIGNMENT);
                    contentBlocks.add(Box.createVerticalStrut(8));
                    contentBlocks.add(codePane);
                }
            }
        }
    }

    private void fireResult(String statusText, boolean allowed) {
        requestActive = false;
        if (onResult != null) {
            onResult.accept(statusText, allowed);
        }
    }

    private void slideOpen() {
        setVisible(true);
        // Let the content determine its natural height first
        revalidate();
        int targetHeight = getPreferredSize().height;
        // Start animation from 0
        setPreferredSize(new Dimension(getParent() != null ? getParent().getWidth() : 400, 0));
        revalidate();

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
    protected void paintComponent(Graphics g) {
        if (wobbleX != 0 || wobbleY != 0) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.translate(wobbleX, wobbleY);
            super.paintComponent(g2d);
            g2d.dispose();
        } else {
            super.paintComponent(g);
        }
    }

    /** Shake the panel side-to-side to attract attention. */
    void startWobble() {
        if (wobbleTimer != null && wobbleTimer.isRunning()) return;

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
                }
            }
        });
        wobbleTimer.start();
    }

    boolean isRequestActive() {
        return requestActive;
    }
}
