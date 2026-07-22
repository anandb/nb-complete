package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Non-modal dialog for searching message history with type-as-you-search filtering.
 * Triggered by Ctrl+R on the chat input area.
 */
// DSL-LEAF: keep imperative, wrap via UI.of(...) — JDialog modal form. Low-risk DSL pilot candidate
// (self-contained; no streaming/timer bridge). Migration target: DialogSpec family.
final class HistorySearchDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final Font DIALOG_FONT = ThemeManager.getFont().deriveFont(13f);
    private static HistorySearchDialog currentInstance;

    private final PlaceholderTextArea inputArea;
    private final JTextField searchField;
    private final JList<String> resultList;
    private final DefaultListModel<String> listModel;
    private final ArrayList<String> allEntries;

    private HistorySearchDialog(Frame owner, PlaceholderTextArea inputArea, MessageHistory messageHistory) {
        super(owner, "History Search", false);
        this.inputArea = inputArea;
        this.allEntries = messageHistory.getEntries(); // newest first
        this.listModel = new DefaultListModel<>();
        this.resultList = new JList<>(listModel);
        this.searchField = new JTextField();

        initComponents();
        loadEntries(allEntries);

        pack();
        setMinimumSize(new Dimension(450, 300));
        setLocationRelativeTo(inputArea);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (currentInstance == HistorySearchDialog.this) {
                    currentInstance = null;
                }
            }
        });
    }

    static void show(PlaceholderTextArea inputArea, MessageHistory messageHistory) {
        if (currentInstance != null && currentInstance.isVisible()) {
            currentInstance.dispose();
            return;
        }
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(inputArea);
        currentInstance = new HistorySearchDialog(owner, inputArea, messageHistory);
        currentInstance.setVisible(true);
    }

    private void initComponents() {
        JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setBorder(new EmptyBorder(12, 12, 8, 12));

        // Search field
        searchField.setFont(DIALOG_FONT);
        searchField.putClientProperty("JTextField.placeholderText", "Type to search history...");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterList(); }
            @Override public void removeUpdate(DocumentEvent e) { filterList(); }
            @Override public void changedUpdate(DocumentEvent e) { filterList(); }
        });

        // Result list
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFont(DIALOG_FONT);
        resultList.setCellRenderer(new HistoryCellRenderer());
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectAndClose();
                }
            }
        });

        // List keyboard navigation
        resultList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    selectAndClose();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    dispose();
                    inputArea.requestFocusInWindow();
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(resultList);
        listScroll.setBorder(BorderFactory.createLineBorder(
                UIManager.getColor("controlShadow")));
        listScroll.setPreferredSize(new Dimension(450, 200));

        contentPanel.add(searchField, BorderLayout.NORTH);
        contentPanel.add(listScroll, BorderLayout.CENTER);

        // Footer hint
        JLabel hint = new JLabel("Up/Down: Navigate   Enter: Select   Esc: Dismiss");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC,
                hint.getFont().getSize() - 1f));
        hint.setForeground(hint.getForeground().brighter());
        hint.setHorizontalAlignment(JLabel.CENTER);
        hint.setBorder(new EmptyBorder(4, 0, 0, 0));
        contentPanel.add(hint, BorderLayout.SOUTH);

        setContentPane(contentPanel);

        // Global key bindings
        getRootPane().registerKeyboardAction(
                e -> {
                    dispose();
                    inputArea.requestFocusInWindow();
                },
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Search field key bindings
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> {
                        e.consume();
                        if (listModel.getSize() > 0) {
                            int idx = resultList.getSelectedIndex();
                            int next = (idx < listModel.getSize() - 1) ? idx + 1 : 0;
                            resultList.setSelectedIndex(next);
                            resultList.ensureIndexIsVisible(next);
                        }
                    }
                    case KeyEvent.VK_UP -> {
                        e.consume();
                        if (listModel.getSize() > 0) {
                            int idx = resultList.getSelectedIndex();
                            int prev = (idx > 0) ? idx - 1 : listModel.getSize() - 1;
                            resultList.setSelectedIndex(prev);
                            resultList.ensureIndexIsVisible(prev);
                        }
                    }
                    case KeyEvent.VK_ENTER -> {
                        e.consume();
                        selectAndClose();
                    }
                    default -> {
                    }
                }
            }
        });
    }

    private void filterList() {
        String query = searchField.getText().trim().toLowerCase();
        listModel.clear();

        if (query.isEmpty()) {
            for (String entry : allEntries) {
                listModel.addElement(entry);
            }
        } else {
            for (String entry : allEntries) {
                if (entry.toLowerCase().contains(query)) {
                    listModel.addElement(entry);
                }
            }
        }

        if (listModel.getSize() > 0) {
            resultList.setSelectedIndex(0);
        }
    }

    private void loadEntries(List<String> entries) {
        listModel.clear();
        for (String entry : entries) {
            listModel.addElement(entry);
        }
        if (!listModel.isEmpty()) {
            resultList.setSelectedIndex(0);
        }
    }

    private void selectAndClose() {
        String selected = resultList.getSelectedValue();
        if (selected != null) {
            inputArea.setText(selected);
            inputArea.setCaretPosition(selected.length());
        }
        dispose();
        inputArea.requestFocusInWindow();
    }

    /** Renderer that truncates long entries and highlights the search term. */
    private static class HistoryCellRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String text) {
                // Truncate display for long entries
                String display = text.length() > 120 ? text.substring(0, 117) + "..." : text;
                // Replace newlines for single-line display
                display = display.replace("\n", " ").replace("\r", "");
                setText("  " + display);
                setFont(DIALOG_FONT);
            }
            return this;
        }
    }

    private static class UIManager {
        static Color getColor(String key) {
            return javax.swing.UIManager.getColor(key);
        }
    }
}
