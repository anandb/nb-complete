package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.BoxLayout;
import javax.swing.Box;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.support.Logger;

import java.util.concurrent.CompletableFuture;

import org.openide.util.NbBundle;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

// DSL-LEAF: not a controller — builds a permission request panel inline.
// Migration target: PermissionBubbleSpec (refs + actions); stays imperative until then.
class PermissionBubble extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.from(PermissionBubble.class);

    PermissionBubble(String prompt, JsonNode options, CompletableFuture<String> responseFuture, JsonNode toolCall) {
        setLayout(new BorderLayout());
        setAlignmentY(Component.CENTER_ALIGNMENT);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        ColorTheme theme = ThemeManager.getCurrentTheme();

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(true);
        content.setBackground(theme.permissionBg());
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.permissionBorder(), 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        String permTitle = NbBundle.getMessage(ChatThreadPanel.class, "LBL_PermissionRequired");
        JLabel titleLabel = new JLabel(permTitle, ThemeManager.getIcon("shield.svg", 18), SwingConstants.LEFT);
        titleLabel.setIconTextGap(8);
        titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(theme.permissionTitle());
        content.add(titleLabel, BorderLayout.NORTH);

        JLabel promptLabel = new JLabel("<html>" + prompt.replace("\n", "<br>") + "</html>");
        promptLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        promptLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(promptLabel);

        if (toolCall != null && toolCall.has("content") && toolCall.get("content").isArray()) {
            for (JsonNode block : toolCall.get("content")) {
                if (block.has("type")) {
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
                        centerPanel.add(Box.createVerticalStrut(10));
                        centerPanel.add(codePane);
                    }
                }
            }
        }

        JScrollPane scrollPane = new JScrollPane(centerPanel) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                d.height = Math.min(d.height, 300);
                return d;
            }
        };
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        content.add(scrollPane, BorderLayout.CENTER);

        int numOptions = (options != null && options.isArray() && options.size() > 0) ? options.size() : 2;
        JPanel buttons = new JPanel(new GridLayout(1, numOptions, 4, 0));
        buttons.setOpaque(false);

        if (options != null && options.isArray() && options.size() > 0) {
            LOG.fine("PermissionBubble: rendering {0} options", options.size());
            for (JsonNode opt : options) {
                String optionId = opt.has("optionId") ? opt.get("optionId").asText() : "";
                String name = opt.has("name") ? opt.get("name").asText() : optionId;
                String kind = opt.has("kind") ? opt.get("kind").asText() : "";

                JButton btn = new JButton(name);
                btn.setFocusPainted(false);
                btn.addActionListener(e -> {
                    responseFuture.complete(optionId);
                    boolean allowed = kind.contains("allow");
                    Icon statusIcon = ThemeManager.getIcon(allowed ? "check.svg" : "x.svg", 16);
                    String statusText = name;
                    Color fg = allowed ? theme.permissionGrantFg() : theme.permissionDenyFg();
                    Color bg = allowed ? theme.permissionGrantBg() : theme.permissionDenyBg();
                    Color border = allowed ? theme.permissionGrantBorder() : theme.permissionDenyBorder();
                    collapse(content, statusText, statusIcon, fg, bg, border);
                });
                buttons.add(btn);
            }
        } else {
            JButton allowBtn = new JButton(NbBundle.getMessage(ChatThreadPanel.class, "BTN_Allow"));
            allowBtn.setFocusPainted(false);

            JButton denyBtn = new JButton(NbBundle.getMessage(ChatThreadPanel.class, "BTN_Deny"));
            denyBtn.setFocusPainted(false);

            allowBtn.addActionListener(e -> {
                responseFuture.complete("allow");
                collapse(content, NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionGranted"), ThemeManager.getIcon("check.svg", 16),
                         theme.permissionGrantFg(), theme.permissionGrantBg(), theme.permissionGrantBorder());
            });

            denyBtn.addActionListener(e -> {
                responseFuture.complete("reject");
                collapse(content, NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionDenied"), ThemeManager.getIcon("x.svg", 16),
                        theme.permissionDenyFg(), theme.permissionDenyBg(), theme.permissionDenyBorder());
            });

            buttons.add(denyBtn);
            buttons.add(allowBtn);
        }

        content.add(buttons, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        setAlignmentX(LEFT_ALIGNMENT);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        if (getParent() != null) {
            int pw = Math.max(getParent().getWidth(), 100);
            return new Dimension((int) (pw * 0.8), pref.height);
        }
        return new Dimension(pref.width, pref.height);
    }

    private void collapse(JPanel content, String status, Icon icon, Color fg, Color bg, Color border) {
        content.removeAll();
        content.setLayout(new BorderLayout());
        content.setBackground(bg);
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border, 1, true),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        JLabel lbl = new JLabel(status, icon, SwingConstants.LEFT);
        lbl.setIconTextGap(8);
        lbl.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        lbl.setForeground(fg);
        content.add(lbl, BorderLayout.CENTER);

        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> {
            Dimension pref = getPreferredSize();
            setMaximumSize(new Dimension(pref.width, pref.height));
        });
    }
}
