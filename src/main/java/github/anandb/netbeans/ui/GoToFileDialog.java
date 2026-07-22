package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import github.anandb.netbeans.support.Logger;
import java.util.regex.Pattern;

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

import org.openide.util.Lookup;
import github.anandb.netbeans.contract.FileCacheQuery;

/**
 * Modal "Go To File" dialog. Case-insensitive prefix/camel-case search
 * across all open projects. Respects VCS ignore rules via the cache.
 *
 * <p>UI: JTextField + JList + status label. Double-click or Enter opens file.</p>
 */
@NbBundle.Messages({
    "LBL_GoToFile=Jump to file",
    "LBL_SearchHint=Type to search files...",
    "# {0} - count",
    "LBL_FilesFound={0} files",
    "# {0} - count",
    "LBL_Indexing=Still Indexing \u2014 Results will be incomplete ({0} files so far)",
    "LBL_NoMatch=No matching files"
})
public class GoToFileDialog extends JDialog {

    private static final Logger LOG = Logger.from(GoToFileDialog.class);
    private static final int MAX_RESULTS = 200;

    private static String lastSearchQuery = "";
    private static long lastCacheVersion = -1;

    private final JTextField searchField;
    private final JList<FileItem> resultList;
    private final DefaultListModel<FileItem> listModel;
    private final JLabel statusLabel;
    private final JScrollPane scrollPane;
    private FileItem selectedItem;

    /** Timer for debounced search — 150ms idle before filtering. */
    private final javax.swing.Timer debounceTimer;

    /** All cached files (snapshot taken at dialog open, refreshed when indexing completes). */
    private List<FileCacheQuery.CachedFile> allFiles;
    private volatile boolean indexingComplete;

    public GoToFileDialog(Window owner) {
        super(owner, Bundle.LBL_GoToFile(), ModalityType.MODELESS);
        FileCacheQuery cache = Lookup.getDefault().lookup(FileCacheQuery.class);
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

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(BorderFactory.createLineBorder(theme.sunkenBackground()));
        scrollPane.setPreferredSize(new Dimension(560, 380));
        this.scrollPane = scrollPane;

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

        long currentVersion = cache.getCacheVersion();
        if (currentVersion == lastCacheVersion) {
            searchField.setText(lastSearchQuery);
            if (!lastSearchQuery.isEmpty()) {
                performSearch();
            }
        } else {
            lastSearchQuery = "";
            lastCacheVersion = currentVersion;
        }

        // --- Listen for indexing completion to refresh results ---
        if (!indexingComplete) {
            Lookup.getDefault().lookup(FileCacheQuery.class).onReady(() -> {
                if (!isDisplayable()) return;
                indexingComplete = true;
                allFiles = new ArrayList<>(cache.getAllFiles());
                javax.swing.SwingUtilities.invokeLater(() -> {
                    updateStatusLabel(searchField.getText());
                    debounceTimer.restart();
                });
            });
        }

        // --- Compute dialog width from the full file cache ---
        resizeToContentWidth();

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
        // Center horizontally, position 30% from the top of the screen
        Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screenSize.width - getWidth()) / 2;
        int y = (int) (screenSize.height * 0.30);
        setLocation(x, y);
    }

    /** Returns the file the user selected, or {@code null} if cancelled. */
    public FileObject getSelectedFile() {
        return selectedItem != null ? selectedItem.fileObject() : null;
    }

    @Override
    public void removeNotify() {
        if (debounceTimer != null) {
            debounceTimer.stop();
        }
        super.removeNotify();
    }

    // --- Search logic ---

    private void updateStatusLabel(String query) {
        if (!indexingComplete) {
            statusLabel.setText(Bundle.LBL_Indexing(allFiles.size()));
        } else if (query == null || query.trim().isEmpty()) {
            statusLabel.setText(Bundle.LBL_FilesFound(allFiles.size()));
        }
    }

    /** Detects glob metacharacters: *, ?, [, { */
    private static final Pattern GLOB_CHARS = Pattern.compile("[*?\\[{]");

    private void performSearch() {
        String query = searchField.getText().trim();
        listModel.clear();

        if (query.isEmpty()) {
            updateStatusLabel(null);
            return;
        }

        // Build a PathMatcher if the query contains glob characters (case-insensitive)
        PathMatcher globMatcher = null;
        if (GLOB_CHARS.matcher(query).find()) {
            try {
                globMatcher = FileSystems.getDefault().getPathMatcher(
                        "glob:" + query.toLowerCase(Locale.ROOT));
            } catch (Exception e) {
                LOG.log(Level.FINE, "Invalid glob pattern: {0}", query);
            }
        }

        // 3-char minimum for plain text; glob patterns are always accepted
        if (globMatcher == null && query.length() < 3) {
            statusLabel.setText(Bundle.LBL_FilesFound(allFiles.size()));
            return;
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        int count = 0;
        for (FileCacheQuery.CachedFile cf : allFiles) {
            if (count >= MAX_RESULTS) break;
            if (matches(cf, lowerQuery, globMatcher)) {
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
     * Resize the dialog width to fit the longest path in the cache, capped at 30% of screen width.
     * Minimum width is 420px. Called once at dialog open.
     */
    private void resizeToContentWidth() {
        java.awt.Font nameFont = resultList.getFont().deriveFont(Font.BOLD);
        java.awt.Font pathFont = resultList.getFont().deriveFont(Font.PLAIN, 11f);
        java.awt.FontMetrics nameFm = resultList.getFontMetrics(nameFont);
        java.awt.FontMetrics pathFm = resultList.getFontMetrics(pathFont);

        int maxTextWidth = 0;
        for (FileCacheQuery.CachedFile cf : allFiles) {
            int nameWidth = nameFm.stringWidth(cf.fileObject().getNameExt());
            int pathWidth = pathFm.stringWidth(cf.projectName() + "/" + cf.relativePath());
            maxTextWidth = Math.max(maxTextWidth, Math.max(nameWidth, pathWidth));
        }

        // Add padding: border(2) + emptyBorder(6*2) + scrollpane border(2) + content border(10*2) + scrollbar(~20)
        int preferred = maxTextWidth + 2 + 12 + 2 + 20 + 20 + 24;
        int minWidth = 420;
        int maxWidth = (int) (Toolkit.getDefaultToolkit().getScreenSize().width * 0.30);
        int newWidth = Math.max(minWidth, Math.min(preferred, maxWidth));

        Dimension current = scrollPane.getPreferredSize();
        scrollPane.setPreferredSize(new Dimension(newWidth, current.height));
        pack();
    }

    /**
     * Case-insensitive matching: prefix on filename, or substring in relative path.
     * When a glob pattern is present, also matches against the relative path using
     * bash-style globs ({@code *}, {@code **}, {@code ?}, {@code [...]}, {@code \{a,b\}}).
     */
    private static boolean matches(FileCacheQuery.CachedFile cf, String lowerQuery,
            PathMatcher globMatcher) {
        String fileName = cf.fileObject().getNameExt().toLowerCase(Locale.ROOT);
        String relPath = cf.relativePath().toLowerCase(Locale.ROOT);

        // Plain text matching (always available)
        if (fileName.startsWith(lowerQuery)) return true;
        if (relPath.contains(lowerQuery)) return true;

        // Glob matching (only when pattern was parseable) — compare lowercased
        if (globMatcher != null) {
            Path path = Path.of(relPath);
            if (globMatcher.matches(path)) return true;
            // Simple globs like *.java or *Test* only match single path elements.
            // Also try matching against just the file name so *.java finds all .java files.
            Path namePath = Path.of(fileName);
            if (globMatcher.matches(namePath)) return true;
        }

        return false;
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
            lastSearchQuery = searchField.getText();
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
            } else if (e.getKeyCode() == KeyEvent.VK_UP && resultList.getSelectedIndex() == 0) {
                searchField.requestFocusInWindow();
                e.consume();
            }
        }
    }

    private class ArrowKeyHandler extends KeyAdapter {
        @Override public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                int size = listModel.getSize();
                if (size > 0) {
                    int idx = resultList.getSelectedIndex();
                    int next = (idx < 0 || idx >= size - 1) ? 0 : idx + 1;
                    resultList.setSelectedIndex(next);
                    resultList.ensureIndexIsVisible(next);
                    resultList.requestFocusInWindow();
                }
                e.consume();
            } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                int idx = resultList.getSelectedIndex();
                if (idx <= 0) {
                    searchField.requestFocusInWindow();
                } else {
                    resultList.setSelectedIndex(idx - 1);
                    resultList.ensureIndexIsVisible(idx - 1);
                }
                e.consume();
            } else if (e.getKeyCode() == KeyEvent.VK_ENTER
                    && !e.isAltDown() && !e.isControlDown() && !e.isShiftDown()) {
                openSelected();
                e.consume();
            }
        }
    }

    // --- Cell renderer: fileName bold, gray path below ---

    private static class FileItemRenderer extends JPanel implements javax.swing.ListCellRenderer<Object> {
        private static final Color PATH_GRAY = new Color(128, 128, 128);
        private final JLabel nameLabel;
        private final JLabel pathLabel;

        FileItemRenderer() {
            super(new BorderLayout(0, 1));
            setBorder(new EmptyBorder(3, 6, 3, 6));

            nameLabel = new JLabel();
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            nameLabel.setOpaque(false);

            pathLabel = new JLabel();
            pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, 11f));
            pathLabel.setOpaque(false);

            add(nameLabel, BorderLayout.NORTH);
            add(pathLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            if (!(value instanceof FileItem item)) {
                return new DefaultListCellRenderer().getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }

            ColorTheme theme = ThemeManager.getCurrentTheme();
            Color bg = isSelected ? theme.selection() : theme.background();
            setBackground(bg);

            // File name
            nameLabel.setText(item.fileObject().getNameExt());
            nameLabel.setForeground(isSelected ? Color.WHITE : theme.foreground());

            // Project + relative path
            pathLabel.setText(item.projectName() + "/" + item.relativePath());
            pathLabel.setForeground(isSelected ? new Color(200, 200, 200) : PATH_GRAY);

            return this;
        }
    }
}
