package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import javax.swing.JTextArea;
import javax.swing.undo.UndoManager;
import javax.swing.event.UndoableEditListener;

import java.util.regex.Pattern;

public class PlaceholderTextArea extends JTextArea {
    private static final long serialVersionUID = 1L;
    private static final Pattern LINE_SPLIT = Pattern.compile("\\R");
    private String placeholder;
    private UndoManager undoManager;

    public PlaceholderTextArea(String placeholder) {
        super();
        this.placeholder = placeholder;
        initUndoManager();
    }

    public PlaceholderTextArea(int rows, int cols, String placeholder) {
        super(rows, cols);
        this.placeholder = placeholder;
        initUndoManager();
    }

    private void initUndoManager() {
        undoManager = new UndoManager();
        getDocument().addUndoableEditListener(undoManager);
    }

    public void undo() {
        if (undoManager.canUndo()) {
            undoManager.undo();
        }
    }

    public void redo() {
        if (undoManager.canRedo()) {
            undoManager.redo();
        }
    }

    public boolean canUndo() {
        return undoManager.canUndo();
    }

    public boolean canRedo() {
        return undoManager.canRedo();
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    public String getPlaceholder() {
        return placeholder;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (placeholder != null && !placeholder.isEmpty() && getText().isEmpty() && !hasFocus()) {
            Color oldColor = g.getColor();
            Font oldFont = g.getFont();
            g.setColor(new Color(150, 150, 150));
            g.setFont(oldFont.deriveFont(Font.PLAIN));
            int x = getInsets().left;
            int y = getInsets().top + g.getFontMetrics().getAscent();
            int lineHeight = g.getFontMetrics().getHeight();
            String[] lines = LINE_SPLIT.split(placeholder, -1);
            for (String line : lines) {
                g.drawString(line, x, y);
                y += lineHeight;
            }
            g.setColor(oldColor);
            g.setFont(oldFont);
        }
    }
}