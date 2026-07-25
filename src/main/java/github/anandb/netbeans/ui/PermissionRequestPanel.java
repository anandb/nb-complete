package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;

// DSL-LEAF: fixed permission request panel below session dropdown, slides open/closed.
final class PermissionRequestPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.from(PermissionRequestPanel.class);

    private static final int SLIDE_STEPS = 8;
    private static final int SLIDE_INTERVAL_MS = 20;
    private static final int PANEL_HEIGHT = 62;

    private final JLabel promptLabel;
    private final JPanel buttonPanel;
    private final JPanel content;
    private CompletableFuture<String> pendingResponse;
    private boolean requestActive = false;

    /** Called when a permission result is ready — receives (statusText, allowed). */
    private java.util.function.BiConsumer<String, Boolean> onResult;

    PermissionRequestPanel() {
        setLayout(new BorderLayout());
        setVisible(false);

        ColorTheme theme = ThemeManager.getCurrentTheme();
        content = new JPanel(new BorderLayout(10, 0));
        content.setOpaque(true);
        content.setBackground(theme.permissionBg());
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, theme.permissionBorder()),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JLabel iconLabel = new JLabel(ThemeManager.getIcon("shield.svg", 18));
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        content.add(iconLabel, BorderLayout.WEST);

        promptLabel = new JLabel(" ");
        promptLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
        promptLabel.setForeground(theme.permissionTitle());
        content.add(promptLabel, BorderLayout.CENTER);

        buttonPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        buttonPanel.setOpaque(false);
        content.add(buttonPanel, BorderLayout.EAST);

        add(content, BorderLayout.CENTER);

        setMaximumSize(new Dimension(Integer.MAX_VALUE, PANEL_HEIGHT));
    }

    void setOnResult(java.util.function.BiConsumer<String, Boolean> onResult) {
        this.onResult = onResult;
    }

    void showRequest(String prompt, JsonNode options, CompletableFuture<String> responseFuture) {
        this.pendingResponse = responseFuture;
        this.requestActive = true;

        promptLabel.setText("<html>" + prompt.replace("\n", "<br>") + "</html>");

        buttonPanel.removeAll();
        buildButtons(options, responseFuture);
        buttonPanel.revalidate();
        buttonPanel.repaint();

        slideOpen();
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

    private void fireResult(String statusText, boolean allowed) {
        requestActive = false;
        if (onResult != null) {
            onResult.accept(statusText, allowed);
        }
    }

    private void slideOpen() {
        setVisible(true);
        setPreferredSize(new Dimension(getParent() != null ? getParent().getWidth() : 400, 0));
        revalidate();

        Timer timer = new Timer(SLIDE_INTERVAL_MS, null);
        final int[] step = {0};
        timer.addActionListener(e -> {
            step[0]++;
            int h = PANEL_HEIGHT * step[0] / SLIDE_STEPS;
            setPreferredSize(new Dimension(getParent() != null ? getParent().getWidth() : 400, h));
            setSize(new Dimension(getWidth(), h));
            revalidate();
            if (step[0] >= SLIDE_STEPS) {
                timer.stop();
                setPreferredSize(new Dimension(Integer.MAX_VALUE, PANEL_HEIGHT));
                revalidate();
            }
        });
        timer.start();
    }

    void slideClose() {
        if (!isVisible()) return;
        Timer timer = new Timer(SLIDE_INTERVAL_MS, null);
        final int[] step = {SLIDE_STEPS};
        timer.addActionListener(e -> {
            step[0]--;
            int h = PANEL_HEIGHT * step[0] / SLIDE_STEPS;
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

    boolean isRequestActive() {
        return requestActive;
    }
}
