package ai.opencode.netbeans.ui;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MessageBubble extends JPanel {
    private final String type;
    private StringBuilder text;
    private final JEditorPane contentPane;

    public MessageBubble(String type, String text) {
        this.type = type;
        this.text = new StringBuilder(text);
        
        setLayout(new GridBagLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(4, 8, 4, 8));

        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();

        contentPane = new JEditorPane();
        contentPane.setEditable(false);
        contentPane.setContentType("text/html");
        // For non-user bubbles (assistant), make it transparent so we don't have a solid block
        contentPane.setOpaque("user".equals(type)); 
        contentPane.setBackground(theme.background);
        
        System.out.println("MessageBubble created: type=" + type + ", text.len=" + text.length());
        updateContent(theme);

        RoundedPanel bubble = new RoundedPanel(16);
        bubble.setLayout(new BorderLayout());
        bubble.setBorder(new EmptyBorder(4, 12, 4, 12));
        bubble.add(contentPane, BorderLayout.CENTER);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        
        if ("user".equals(type)) {
            bubble.setBackground(theme.bubbleUser);
            bubble.setBaseColor(theme.bubbleUser);
            gbc.anchor = GridBagConstraints.EAST;
        } else if ("error".equals(type)) {
            Color errorBg = new Color(255, 235, 238); // Material Red 50
            bubble.setBackground(errorBg);
            bubble.setBaseColor(errorBg);
            gbc.anchor = GridBagConstraints.WEST;
            bubble.setBorder(new EmptyBorder(4, 12, 4, 12));
            contentPane.setOpaque(true);
        } else {
            // Assistant/System: No background as requested
            bubble.setBackground(new Color(0,0,0,0));
            bubble.setBaseColor(null);
            gbc.anchor = GridBagConstraints.WEST;
            // For assistant, remove border as well
            bubble.setBorder(new EmptyBorder(4, 0, 4, 12));
        }
        
        // Allow expansion up to available width, but let height be determined by content
        // bubble.setMaximumSize(...) is removed to allow height to grow with wrapped text
        
        add(bubble, gbc);
    }

    private static class RoundedPanel extends JPanel {
        private int radius;
        private Color baseColor;

        public RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }
        
        public void setBaseColor(Color color) {
            this.baseColor = color;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            if (baseColor != null) {
                g2.setColor(baseColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            }
            
            // Subtle border
            ThemeManager.Theme theme = ThemeManager.getCurrentTheme();
            if (theme.bubbleBorder != null && theme.bubbleBorder.getAlpha() > 0 && baseColor != null) {
                g2.setColor(theme.bubbleBorder);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
        }
    }

    public void appendText(String newText) {
        this.text.append(newText);
        updateContent(ThemeManager.getCurrentTheme());
    }

    public String getType() {
        return type;
    }

    private void updateContent(ThemeManager.Theme theme) {
        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        String rawText = text.toString();
        String html = renderer.render(parser.parse(rawText));
        
        // Fix for Swing HTMLEditorKit: <pre> tags do not compute height correctly when wrapped.
        // We replace <pre> with a stylized <div> and convert newlines/spaces manually.
        html = processPreTagsForSwingWrapping(html, theme);
        
        Color bg;
        if ("user".equals(type)) {
            bg = theme.bubbleUser;
        } else if ("error".equals(type)) {
            bg = new Color(255, 235, 238);
        } else {
            bg = theme.background;
        }
        if (bg == null) bg = Color.WHITE;
        contentPane.setBackground(bg);
        boolean isAssistant = !"user".equals(type) && !"error".equals(type);
        
        String customCss = theme.toCss(bg, isAssistant);
        if ("error".equals(type)) {
            customCss += " body { color: #D32F2F; font-weight: bold; }";
        }
        
        String styledHtml = "<html><head><style>" + customCss + "</style></head><body style='font-family: sans-serif;'>" + html + "</body></html>";
        contentPane.setText(styledHtml);
        
        // Force the JEditorPane to compute layout
        contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        
        SwingUtilities.invokeLater(() -> {
            contentPane.revalidate();
            contentPane.repaint();
        });
    }
    private String processPreTagsForSwingWrapping(String html, ThemeManager.Theme theme) {
        StringBuilder result = new StringBuilder();
        int lastIndex = 0;
        int preStart = html.indexOf("<pre>");
        while (preStart != -1) {
            int preEnd = html.indexOf("</pre>", preStart);
            if (preEnd == -1) break;
            
            result.append(html.substring(lastIndex, preStart));
            
            String preContent = html.substring(preStart + 5, preEnd);
            
            // Remove <code> and </code> if they exist directly inside <pre>
            if (preContent.startsWith("<code>")) {
                preContent = preContent.substring(6);
            } else if (preContent.startsWith("<code ")) {
                int codeEnd = preContent.indexOf(">");
                if (codeEnd != -1) preContent = preContent.substring(codeEnd + 1);
            }
            if (preContent.endsWith("</code>")) {
                preContent = preContent.substring(0, preContent.length() - 7);
            }
            
            // Replace newlines with <br>
            preContent = preContent.replace("\n", "<br>");
            
            // Fix spaces for Swing: alternating ' ' and '&nbsp;' to allow wrapping but preserve layout
            StringBuilder spaceFixed = new StringBuilder();
            boolean lastWasSpace = false;
            for (int i = 0; i < preContent.length(); i++) {
                char c = preContent.charAt(i);
                if (c == ' ') {
                    if (lastWasSpace) {
                        spaceFixed.append("&nbsp;");
                        lastWasSpace = false;
                    } else {
                        spaceFixed.append(" ");
                        lastWasSpace = true;
                    }
                } else if (c == '\t') {
                    // Standard 4 space tab
                    spaceFixed.append(" &nbsp; &nbsp;");
                    lastWasSpace = false;
                } else {
                    spaceFixed.append(c);
                    lastWasSpace = false;
                }
            }
            
            result.append("<div style=\"font-family: 'JetBrains Mono', 'Cascadia Code', monospace; font-size: 13px; background-color: #e9e9d0; padding: 12px; margin: 12px 0; border-radius: 8px; border: 1px solid rgba(128,128,128,0.15);\">");
            result.append(spaceFixed.toString());
            result.append("</div>");
            
            lastIndex = preEnd + 6;
            preStart = html.indexOf("<pre>", lastIndex);
        }
        result.append(html.substring(lastIndex));
        return result.toString();
    }
}
