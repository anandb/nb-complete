package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

import java.util.regex.Pattern;

import github.anandb.netbeans.support.Logger;

// DSL-LEAF: keep imperative, wrap via UI.of(...) — paintComponent placeholder overlay + word-wise undo. Not ported to withStyle.
public class PlaceholderTextArea extends JTextArea implements Scrollable {
    private static final long serialVersionUID = 1L;
    private static final Pattern LINE_SPLIT = Pattern.compile("\\R");
    private static final Logger LOG = Logger.from(PlaceholderTextArea.class);
    private String placeholder;
    private UndoManager undoManager;
    private WordBoundaryEditListener editListener;
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
        editListener = new WordBoundaryEditListener();
        getDocument().addUndoableEditListener(editListener);
        installKeyBindings();
    }

    private void installKeyBindings() {
        InputMap im = getInputMap(WHEN_FOCUSED);
        ActionMap am = getActionMap();

        Action undoAction = new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { undo(); }
        };
        Action redoAction = new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { redo(); }
        };

        // Ctrl+Z / Cmd+Z for undo
        im.put(KeyStroke.getKeyStroke("control Z"), "undo");
        im.put(KeyStroke.getKeyStroke("meta Z"), "undo");
        am.put("undo", undoAction);

        // Ctrl+Y / Cmd+Y / Shift+Ctrl+Z / Shift+Cmd+Z for redo
        im.put(KeyStroke.getKeyStroke("control Y"), "redo");
        im.put(KeyStroke.getKeyStroke("meta Y"), "redo");
        im.put(KeyStroke.getKeyStroke("shift control Z"), "redo");
        im.put(KeyStroke.getKeyStroke("shift meta Z"), "redo");
        am.put("redo", redoAction);
    }

    /**
     * Groups consecutive character edits into word-level {@link CompoundEdit}s.
     * A whitespace character ends the current compound and starts a new one,
     * so each undo step removes one word (or continuous non-whitespace run).
     * Deletes and pastes end the current compound and create their own step.
     */
    private class WordBoundaryEditListener implements UndoableEditListener {
        private CompoundEdit compound;

        @Override
        public void undoableEditHappened(UndoableEditEvent e) {
            UndoableEdit edit = e.getEdit();

            if (edit instanceof AbstractDocument.DefaultDocumentEvent docEvent) {
                if (docEvent.getType() == DocumentEvent.EventType.INSERT) {
                    // Check if this insert hits a word boundary (whitespace)
                    if (insertContainsWhitespace(docEvent)) {
                        endCompound();
                    }
                } else {
                    // REMOVE or CHANGE: end current compound so delete/paste is its own step
                    endCompound();
                }
            }

            if (compound == null) {
                compound = new CompoundEdit();
                compound.addEdit(edit);
                undoManager.addEdit(compound);
            } else {
                compound.addEdit(edit);
            }
        }

        private void endCompound() {
            if (compound != null) {
                compound.end();
                compound = null;
            }
        }

        private boolean insertContainsWhitespace(AbstractDocument.DefaultDocumentEvent docEvent) {
            try {
                String text = docEvent.getDocument().getText(
                    docEvent.getOffset(), docEvent.getLength());
                for (int i = 0; i < text.length(); i++) {
                    if (Character.isWhitespace(text.charAt(i))) return true;
                }
            } catch (BadLocationException ignored) {
                // Ignore this exception
            }
            return false;
        }
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
        if (editListener != null) {
            editListener.endCompound();
        }
        try {
            if (undoManager.canUndo()) {
                undoManager.undo();
            }
        } catch (CannotUndoException e) {
            LOG.warn("Cannot undo: {0}", ExceptionUtils.getMessage(e));
        }
    }

    public void redo() {
        if (editListener != null) {
            editListener.endCompound();
        }
        try {
            if (undoManager.canRedo()) {
                undoManager.redo();
            }
        } catch (CannotRedoException e) {
            LOG.warn("Cannot redo: {0}", ExceptionUtils.getMessage(e));
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

    // --- Scrollable implementation ---

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        // Let JTextArea compute its natural preferred size based on content.
        // Starts at ~2 lines when empty, grows as user types.
        return super.getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return getFontMetrics(getFont()).getHeight();
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return visibleRect.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true; // Always fill the scroll pane width — text wraps
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false; // Let the scroll pane grow vertically as content grows
    }
}