package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.text.DefaultEditorKit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import java.util.regex.Pattern;

import github.anandb.netbeans.support.Logger;

public class PlaceholderTextArea extends JTextArea {
    private static final long serialVersionUID = 1L;
    private static final Pattern LINE_SPLIT = Pattern.compile("\\R");
    private static final Logger LOG = Logger.from(PlaceholderTextArea.class);
    private String placeholder;
    private UndoManager undoManager;
    private transient JPopupMenu contextMenu;

    public PlaceholderTextArea(String placeholder) {
        super();
        this.placeholder = placeholder;
        initUndoManager();
        getAccessibleContext().setAccessibleName("Chat input");
        getAccessibleContext().setAccessibleDescription("Type your message here");
    }

    public PlaceholderTextArea(int rows, int cols, String placeholder) {
        super(rows, cols);
        this.placeholder = placeholder;
        initUndoManager();
        getAccessibleContext().setAccessibleName("Chat input");
        getAccessibleContext().setAccessibleDescription("Type your message here");
    }

    private void initUndoManager() {
        undoManager = new UndoManager();
        getDocument().addUndoableEditListener(undoManager);
    }

    @Override
    public JPopupMenu getComponentPopupMenu() {
        if (contextMenu == null) {
            contextMenu = buildContextMenu();
        }
        return contextMenu;
    }

    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        ActionMap map = getActionMap();
        addOpt(menu, map, DefaultEditorKit.cutAction);
        addOpt(menu, map, DefaultEditorKit.copyAction);
        addOpt(menu, map, DefaultEditorKit.pasteAction);
        menu.addSeparator();
        addOpt(menu, map, DefaultEditorKit.selectAllAction);
        return menu;
    }

    private static void addOpt(JPopupMenu menu, ActionMap map, String key) {
        Action a = map.get(key);
        if (a != null) {
            menu.add(a);
        }
    }

    public void undo() {
        try {
            if (undoManager.canUndo()) {
                undoManager.undo();
            }
        } catch (CannotUndoException e) {
            LOG.warn("Cannot undo: {0}", e.getMessage());
        }
    }

    public void redo() {
        try {
            if (undoManager.canRedo()) {
                undoManager.redo();
            }
        } catch (CannotRedoException e) {
            LOG.warn("Cannot redo: {0}", e.getMessage());
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
            g.setColor(ThemeManager.getCurrentTheme().placeholderForeground());
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