package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ToolCallDiffParser;
import github.anandb.netbeans.support.ToolCallDiffParser.FileChange;
import org.openide.util.NbBundle;

// DSL-LEAF: not a controller — builds a permission request panel inline.
// Migration target: PermissionBubbleSpec (refs + actions); stays imperative until then.
@NbBundle.Messages({
    "BTN_ShowDiff=Show Diff"
})
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

        // File list from tool call diffs
        List<FileChange> fileChanges = toolCall != null
                ? ToolCallDiffParser.parse(toolCall) : List.of();
        if (!fileChanges.isEmpty()) {
            Font mono = IconResourceManager.getMonospaceFont();
            centerPanel.add(Box.createVerticalStrut(6));
            // Glanceable summary: count changes by type
            long added = fileChanges.stream().filter(fc -> fc.status() == 'A').count();
            long deleted = fileChanges.stream().filter(fc -> fc.status() == 'D').count();
            long modified = fileChanges.stream().filter(fc -> fc.status() == 'M').count();
            StringBuilder summary = new StringBuilder();
            summary.append(fileChanges.size()).append(" file").append(fileChanges.size() != 1 ? "s" : "").append(" changed");
            if (modified > 0) summary.append(" (").append(modified).append(" modified");
            if (added > 0) summary.append(modified > 0 ? ", " : " (").append(added).append(" added");
            if (deleted > 0) summary.append((modified > 0 || added > 0) ? ", " : " (").append(deleted).append(" deleted");
            if (modified > 0 || added > 0 || deleted > 0) summary.append(")");
            JLabel summaryLabel = new JLabel(summary.toString());
            summaryLabel.setFont(mono);
            summaryLabel.setForeground(theme.permissionTitle().darker());
            summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            centerPanel.add(summaryLabel);
            centerPanel.add(Box.createVerticalStrut(4));
            for (FileChange fc : fileChanges) {
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
                row.setOpaque(false);

                JLabel nameLabel = new JLabel(PermissionRequestPanel.displayPath(fc.filePath()));
                nameLabel.setFont(mono);
                nameLabel.setIcon(ThemeManager.getIcon("file.svg", 14));
                nameLabel.setIconTextGap(5);
                nameLabel.setToolTipText(fc.filePath());

                char st = fc.status();
                Color stCol = st == 'A' ? new Color(0x28a745)
                        : st == 'D' ? new Color(0xd73a49) : new Color(0x0366d6);
                JLabel stLabel = new JLabel("(" + st + ")");
                stLabel.setFont(mono);
                stLabel.setForeground(stCol);

                row.add(nameLabel);
                row.add(stLabel);
                centerPanel.add(row);
            }
        }

        // Text-only content blocks (non-diff) shown inline
        if (toolCall != null && toolCall.has("content") && toolCall.get("content").isArray()) {
            for (JsonNode block : toolCall.get("content")) {
                if (!block.has("type")) continue;
                String type = block.get("type").asText();
                if ("text".equals(type) && block.has("text")) {
                    CollapsibleCodePane codePane = new CollapsibleCodePane("text",
                            block.get("text").asText().trim(), true);
                    codePane.setAlignmentX(Component.LEFT_ALIGNMENT);
                    centerPanel.add(Box.createVerticalStrut(6));
                    centerPanel.add(codePane);
                }
            }
        }

        // Changes list for Show Diff button
        final List<FileChange> changes = fileChanges;

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

        boolean hasChanges = !changes.isEmpty();
        int extra = hasChanges ? 1 : 0;
        int numOptions = (options != null && options.isArray() && options.size() > 0)
                ? options.size() + extra : 2 + extra;
        JPanel buttons = new JPanel(new GridLayout(1, numOptions, 4, 0));
        buttons.setOpaque(false);

        if (hasChanges) {
            JButton showDiffBtn = new JButton(Bundle.BTN_ShowDiff());
            showDiffBtn.setFocusPainted(false);
            showDiffBtn.addActionListener(e -> PermissionRequestPanel.openDiffView(changes));
            buttons.add(showDiffBtn);
        }

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
