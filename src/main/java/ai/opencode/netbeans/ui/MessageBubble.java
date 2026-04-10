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
        contentPane.setOpaque(false);
        
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

        String html = renderer.render(parser.parse(text.toString()));
        
        String styledHtml = "<html><head><style>" + theme.toCss() + "</style></head><body>" + html + "</body></html>";
        
        contentPane.setText(styledHtml);
    }
}
