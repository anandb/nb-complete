package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
import javax.swing.event.DocumentListener;
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
    private String overlayText;

    public PlaceholderTextArea(String placeholder) {
        super();
        this.placeholder = placeholder;
        initUndoManager();
        initDocumentListener();
        getAccessibleContext().setAccessibleName("Chat input");
        getAccessibleContext().setAccessibleDescription("Type your message here");
    }

    public PlaceholderTextArea(int rows, int cols, String placeholder) {
        super(rows, cols);
        this.placeholder = placeholder;
        initUndoManager();
        initDocumentListener();
        getAccessibleContext().setAccessibleName("Chat input");
        getAccessibleContext().setAccessibleDescription("Type your message here");
    }

    private void initDocumentListener() {
        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { repaint(); }
            @Override public void removeUpdate(DocumentEvent e) { repaint(); }
            @Override public void changedUpdate(DocumentEvent e) { repaint(); }
        });
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

    public void setOverlayText(String text) {
        this.overlayText = text;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (overlayText != null && !overlayText.isEmpty() && getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(ThemeManager.getCurrentTheme().placeholderForeground());
            Font overlayFont = getFont().deriveFont(Math.max(9f, getFont().getSize() - 2f));
            g2.setFont(overlayFont);
            FontMetrics fm = g2.getFontMetrics();
            
            int availableWidth = getWidth() - getInsets().left - getInsets().right - 8;
            String[] lines;
            if (fm.stringWidth(overlayText) > availableWidth) {
                int midPoint = overlayText.length() / 2;
                int splitRight = overlayText.indexOf(" | ", midPoint);
                int splitLeft = overlayText.lastIndexOf(" | ", midPoint);
                
                int splitIndex = -1;
                if (splitRight != -1 && splitLeft != -1) {
                    splitIndex = (splitRight - midPoint) < (midPoint - splitLeft) ? splitRight : splitLeft;
                } else if (splitRight != -1) {
                    splitIndex = splitRight;
                } else {
                    splitIndex = splitLeft;
                }

                if (splitIndex != -1) {
                    lines = new String[] {
                        overlayText.substring(0, splitIndex).trim(),
                        overlayText.substring(splitIndex + 3).trim()
                    };
                } else {
                    lines = new String[] { overlayText };
                }
            } else {
                lines = new String[] { overlayText };
            }

            int y = getInsets().top + fm.getAscent();
            for (String line : lines) {
                int lineWidth = fm.stringWidth(line);
                int x = (getWidth() - lineWidth) / 2;
                g2.drawString(line, x, y);
                y += fm.getHeight();
            }
            g2.dispose();
        } else if (placeholder != null && !placeholder.isEmpty() && getText().isEmpty() && !hasFocus()) {
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