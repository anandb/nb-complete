package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
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
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;

import github.anandb.netbeans.contract.FileCacheQuery;
import github.anandb.netbeans.manager.FileCacheManager;

/**
 * Modal "Go To File" dialog. Case-insensitive prefix/camel-case search
 * across all open projects. Respects VCS ignore rules via the cache.
 *
 * <p>UI: JTextField + JList + status label. Double-click or Enter opens file.</p>
 */
@NbBundle.Messages({
    "LBL_GoToFile=Go To File",
    "LBL_SearchHint=Type to search files...",
    "# {0} - count",
    "LBL_FilesFound={0} files",
    "# {0} - count",
    "LBL_Indexing=Still Indexing \u2014 Results will be incomplete ({0} files so far)",
    "LBL_NoMatch=No matching files"
})
public class GoToFileDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(GoToFileDialog.class.getName());
    private static final int MAX_RESULTS = 200;

    private final JTextField searchField;
    private final JList<FileItem> resultList;
    private final DefaultListModel<FileItem> listModel;
    private final JLabel statusLabel;
    private FileItem selectedItem;

    /** Timer for debounced search — 150ms idle before filtering. */
    private final javax.swing.Timer debounceTimer;

    /** All cached files (snapshot taken at dialog open, refreshed when indexing completes). */
    private List<FileCacheQuery.CachedFile> allFiles;
    private volatile boolean indexingComplete;

    public GoToFileDialog(Window owner) {
        super(owner, Bundle.LBL_GoToFile(), ModalityType.APPLICATION_MODAL);
        FileCacheQuery cache = FileCacheManager.getDefault();
        this.allFiles = new ArrayList<>(cache.getAllFiles());
        this.indexingComplete = cache.isReady();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        ColorTheme theme = ThemeManager.getCurrentTheme();

        // --- Search field ---
        searchField = new JTextField();
        searchField.setFont(ThemeManager.getMonospaceFont());
        searchField.putClientProperty("JTextField.placeholderText", Bundle.LBL_SearchHint());
        searchField.addKeyListener(new ArrowKeyHandler());
        searchField.getDocument().addDocumentListener(new SearchUpdater());

        // --- Result list ---
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new FileItemRenderer());
        resultList.addMouseListener(new DoubleClickOpener());
        resultList.addKeyListener(new EnterKeyOpener());
        // Pre-select first item when results arrive
        listModel.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override public void intervalAdded(javax.swing.event.ListDataEvent e) {
                if (listModel.getSize() == 1) {
                    resultList.setSelectedIndex(0);
                }
            }
            @Override public void intervalRemoved(javax.swing.event.ListDataEvent e) {}
            @Override public void contentsChanged(javax.swing.event.ListDataEvent e) {}
        });

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(BorderFactory.createLineBorder(theme.sunkenBackground()));
        scrollPane.setPreferredSize(new Dimension(560, 380));

        // --- Status label ---
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(theme.mutedForeground());
        statusLabel.setBorder(new EmptyBorder(4, 2, 0, 2));
        updateStatusLabel(null);

        // --- Layout ---
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        content.setBackground(theme.background());
        content.add(searchField, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(content);

        // --- Debounce timer ---
        debounceTimer = new javax.swing.Timer(150, e -> performSearch());
        debounceTimer.setRepeats(false);

        // --- Listen for indexing completion to refresh results ---
        if (!indexingComplete) {
            FileCacheManager.getDefault().onReady(() -> {
                if (!isDisplayable()) return;
                indexingComplete = true;
                allFiles = new ArrayList<>(cache.getAllFiles());
                javax.swing.SwingUtilities.invokeLater(() -> {
                    updateStatusLabel(searchField.getText());
                    debounceTimer.restart();
                });
            });
        }

        // --- Focus ---
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                searchField.requestFocusInWindow();
            }
        });

        // Escape to close
        searchField.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        searchField.getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });

        pack();
        setLocationRelativeTo(owner);
    }

    /** Returns the file the user selected, or {@code null} if cancelled. */
    public FileObject getSelectedFile() {
        return selectedItem != null ? selectedItem.fileObject() : null;
    }

    // --- Search logic ---

    private void updateStatusLabel(String query) {
        if (!indexingComplete) {
            statusLabel.setText(Bundle.LBL_Indexing(allFiles.size()));
        } else if (query == null || query.trim().isEmpty()) {
            statusLabel.setText(Bundle.LBL_FilesFound(allFiles.size()));
        }
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        listModel.clear();

        if (query.isEmpty()) {
            updateStatusLabel(null);
            return;
        }

        if (query.length() < 3) {
            statusLabel.setText(Bundle.LBL_FilesFound(allFiles.size()));
            return;
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        int count = 0;
        for (FileCacheQuery.CachedFile cf : allFiles) {
            if (count >= MAX_RESULTS) break;
            if (matches(cf, lowerQuery)) {
                listModel.addElement(new FileItem(
                    cf.fileObject(), cf.projectName(), cf.relativePath()));
                count++;
            }
        }

        if (count == 0) {
            statusLabel.setText(Bundle.LBL_NoMatch());
        } else if (!indexingComplete) {
            statusLabel.setText(Bundle.LBL_Indexing(allFiles.size()));
        } else {
            statusLabel.setText(Bundle.LBL_FilesFound(count));
        }
    }

    /**
     * Case-insensitive matching: prefix on filename, or substring in relative path.
     */
    private static boolean matches(FileCacheQuery.CachedFile cf, String lowerQuery) {
        String fileName = cf.fileObject().getNameExt().toLowerCase(Locale.ROOT);
        String relPath = cf.relativePath().toLowerCase(Locale.ROOT);

        if (fileName.startsWith(lowerQuery)) return true;
        return relPath.contains(lowerQuery);
    }

    // --- Inner types ---

    private record FileItem(FileObject fileObject, String projectName, String relativePath) {}

    private void openSelected() {
        FileItem item = resultList.getSelectedValue();
        if (item == null) return;
        selectedItem = item;
        dispose();
        // Open in editor
        try {
            DataObject.find(item.fileObject()).getLookup()
                .lookup(org.openide.cookies.OpenCookie.class)
                .open();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to open file: {0}", item.fileObject());
        }
    }

    // --- Event handlers ---

    private class SearchUpdater implements DocumentListener {
        @Override public void insertUpdate(DocumentEvent e)  { scheduleSearch(); }
        @Override public void removeUpdate(DocumentEvent e)   { scheduleSearch(); }
        @Override public void changedUpdate(DocumentEvent e)  { scheduleSearch(); }
        private void scheduleSearch() {
            debounceTimer.restart();
        }
    }

    private class DoubleClickOpener extends MouseAdapter {
        @Override public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                openSelected();
            }
        }
    }

    private class EnterKeyOpener extends KeyAdapter {
        @Override public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER
                    && !e.isAltDown() && !e.isControlDown() && !e.isShiftDown()) {
                openSelected();
                e.consume();
            }
        }
    }

    private class ArrowKeyHandler extends KeyAdapter {
        @Override public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                int idx = resultList.getSelectedIndex();
                if (idx < listModel.getSize() - 1) {
                    resultList.setSelectedIndex(idx + 1);
                    resultList.ensureIndexIsVisible(idx + 1);
                }
                e.consume();
            } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                int idx = resultList.getSelectedIndex();
                if (idx > 0) {
                    resultList.setSelectedIndex(idx - 1);
                    resultList.ensureIndexIsVisible(idx - 1);
                }
                e.consume();
            }
        }
    }

    // --- Cell renderer: fileName bold, gray path below ---

    private static class FileItemRenderer extends DefaultListCellRenderer {
        private static final Color PATH_GRAY = new Color(128, 128, 128);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            if (!(value instanceof FileItem item)) {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }

            JPanel panel = new JPanel(new BorderLayout(0, 1));
            panel.setBorder(new EmptyBorder(3, 6, 3, 6));

            ColorTheme theme = ThemeManager.getCurrentTheme();
            Color bg = isSelected ? theme.selection() : theme.background();
            panel.setBackground(bg);

            // File name (bold)
            JLabel nameLabel = new JLabel(item.fileObject().getNameExt());
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            nameLabel.setForeground(isSelected ? Color.WHITE : theme.foreground());
            nameLabel.setOpaque(false);

            // Project + relative path (gray)
            JLabel pathLabel = new JLabel(item.projectName() + "/" + item.relativePath());
            pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, 11f));
            pathLabel.setForeground(isSelected ? new Color(200, 200, 200) : PATH_GRAY);
            pathLabel.setOpaque(false);

            panel.add(nameLabel, BorderLayout.NORTH);
            panel.add(pathLabel, BorderLayout.SOUTH);

            return panel;
        }
    }
}
