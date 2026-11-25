package github.anandb.netbeans.ui.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
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
import java.util.function.Consumer;
import java.util.Locale;
import java.util.logging.Level;
import github.anandb.netbeans.support.Logger;

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
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;

import github.anandb.netbeans.contract.FileCacheQuery;
import github.anandb.netbeans.pattern.FileNameMatcher;
import github.anandb.netbeans.ui.rendering.ColorTheme;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.platform.PlatformBridge;

/**
 * Modeless "Go To File" dialog. Case-insensitive search across the cached index of every open
 * project: a plain query matches a file-name prefix or any substring of the project-relative path,
 * and a query containing glob characters is matched with NIO's glob syntax — see
 * {@link FileNameMatcher}. Respects VCS ignore rules via the cache.
 *
 * <p>UI: JTextField + JList + status label. Double-click or Enter opens file.
 * In picker mode (constructor with a selection consumer) Enter confirms every selected
 * file and double-click confirms the clicked file; the dialog stays open for more
 * picks and the selection clears after each attach.</p>
 */
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
    /** Non-null = attach/pick mode: multi-select, selection handed to the
     *  consumer, no editor opened. Null = legacy jump-to-file mode. */
    private final Consumer<List<FileObject>> selectionConsumer;

    /** Timer for debounced search — 150ms idle before filtering. */
    private final Timer debounceTimer;

    /** Runs the index scan off the EDT; publishes each surviving result here. */
    private final FileIndexScanner scanner;

    /** All cached files (snapshot taken at dialog open, refreshed when indexing completes). */
    private volatile List<FileCacheQuery.CachedFile> allFiles;
    private volatile boolean indexingComplete;

    public GoToFileDialog(Window owner) {
        this(owner, null);
    }

    /** Picker mode: one or more files are handed to {@code selectionConsumer}. */
    public GoToFileDialog(Window owner, Consumer<List<FileObject>> selectionConsumer) {
        super(owner, NbBundle.getMessage(UIBundle.class, "LBL_GoToFile"), ModalityType.MODELESS);
        this.selectionConsumer = selectionConsumer;
        if (selectionConsumer != null) {
            setTitle(NbBundle.getMessage(UIBundle.class, "LBL_SelectProjectFiles"));
        }
        setTitle(getTitle() + " " + NbBundle.getMessage(UIBundle.class, "LBL_EscCloseHint"));
        FileCacheQuery cache = PlatformBridge.serviceSafe(FileCacheQuery.class);
        this.allFiles = new ArrayList<>(cache.getAllFiles());
        this.indexingComplete = cache.isReady();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        ColorTheme theme = ThemeManager.getCurrentTheme();

        // --- Search field ---
        searchField = new JTextField();
        searchField.setFont(ThemeManager.getMonospaceFont());
        searchField.putClientProperty("JTextField.placeholderText", NbBundle.getMessage(UIBundle.class, "LBL_SearchHint"));
        searchField.addKeyListener(new ArrowKeyHandler());
        searchField.getDocument().addDocumentListener(new SearchUpdater());

        // --- Result list ---
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setSelectionMode(selectionConsumer == null
                ? ListSelectionModel.SINGLE_SELECTION : ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
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
        debounceTimer = new Timer(150, e -> performSearch());
        debounceTimer.setRepeats(false);

        scanner = FileIndexScanner.withDefaultExecutor(this::publishResults);

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
            PlatformBridge.serviceSafe(FileCacheQuery.class).onReady(() -> {
                if (!isDisplayable()) return;
                indexingComplete = true;
                allFiles = new ArrayList<>(cache.getAllFiles());
                SwingUtilities.invokeLater(() -> {
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
                // Select the restored prior query so typing replaces it;
                // arrow keys still let the user edit it in place.
                searchField.selectAll();
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
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
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
        List<FileCacheQuery.CachedFile> files = allFiles;
        if (!indexingComplete) {
            statusLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_Indexing", files.size()));
        } else if (query == null || query.trim().isEmpty()) {
            statusLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_FilesFound", files.size()));
        }
    }

    private void performSearch() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            listModel.clear();
            updateStatusLabel(null);
            return;
        }
        // The scan itself runs off the EDT; a newer query supersedes this one.
        scanner.search(text, allFiles, MAX_RESULTS);
    }

    /**
     * Publishes one finished scan on the EDT. Only the newest scan reaches here
     * (the scanner drops superseded generations), so the list model is cleared
     * and refilled in one step instead of mid-scan.
     */
    private void publishResults(FileIndexScanner.ScanResult result) {
        listModel.clear();

        // A plain query shorter than the minimum matches too much to be a search, and a
        // glob short enough to get here was malformed. Say so: showing the index size here
        // read as a result count and made it look as if the list had been searched.
        if (!result.usable()) {
            statusLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_SearchTooShort",
                    FileNameMatcher.MIN_PLAIN_LENGTH));
            LOG.fine("Jump-to-file search skipped for ''{0}'': plain query shorter than {1} "
                    + "characters and not a usable glob", result.query(),
                    FileNameMatcher.MIN_PLAIN_LENGTH);
            return;
        }

        int count = 0;
        for (FileCacheQuery.CachedFile cf : result.hits()) {
            listModel.addElement(new FileItem(cf.fileObject(), cf.projectName(), cf.relativePath()));
            count++;
        }

        if (count == 0) {
            statusLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_NoMatch"));
        } else if (!indexingComplete) {
            statusLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_Indexing", result.indexedCount()));
        } else {
            statusLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_FilesFound", count));
        }
        LOG.fine("Jump-to-file search for ''{0}'': {1} result(s) from {2} indexed file(s) in {3} ms",
                result.query(), count, result.indexedCount(),
                String.format(Locale.ROOT, "%.2f", result.elapsedNanos() / 1_000_000.0));
    }

    /**
     * Resize the dialog width to fit the longest path in the cache, capped at 30% of screen width.
     * Minimum width is 420px. Called once at dialog open.
     */
    private void resizeToContentWidth() {
        Font nameFont = resultList.getFont().deriveFont(Font.BOLD);
        Font pathFont = resultList.getFont().deriveFont(Font.PLAIN, 11f);
        FontMetrics nameFm = resultList.getFontMetrics(nameFont);
        FontMetrics pathFm = resultList.getFontMetrics(pathFont);

        int maxTextWidth = maxTextWidth(allFiles, nameFm, pathFm);

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
     * Widest text the result list must fit. Picks the longest name and the longest
     * project/path by character count first and measures only those two, so opening the
     * dialog is an O(N) length scan instead of an O(N) text-layout pass on the EDT. The
     * caller clamps the result, so the character-count heuristic is exact enough.
     */
    static int maxTextWidth(List<FileCacheQuery.CachedFile> files, FontMetrics nameFm, FontMetrics pathFm) {
        String longestName = "";
        String longestPath = "";
        for (FileCacheQuery.CachedFile cf : files) {
            String name = cf.fileObject().getNameExt();
            if (name.length() > longestName.length()) {
                longestName = name;
            }
            int pathLen = cf.projectName().length() + 1 + cf.relativePath().length();
            if (pathLen > longestPath.length()) {
                longestPath = cf.projectName() + "/" + cf.relativePath();
            }
        }
        return Math.max(nameFm.stringWidth(longestName), pathFm.stringWidth(longestPath));
    }

    // --- Inner types ---

    /** Picker mode: hand the selected files to the consumer and stay open, so
     *  more files can be picked; the selection is cleared after each attach. */
    private void confirmSelection() {
        List<FileItem> items = resultList.getSelectedValuesList();
        if (items.isEmpty()) return;
        List<FileObject> files = new ArrayList<>(items.size());
        for (FileItem item : items) {
            files.add(item.fileObject());
        }
        resultList.clearSelection();
        selectionConsumer.accept(files);
        statusLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_PickerAttached"));
        // Everything displayed has been picked (and there was something to pick):
        // start a fresh query. Zero results never clear the typed query.
        if (listModel.size() > 0 && items.size() == listModel.size()) {
            searchField.setText("");
        }
    }

    private record FileItem(FileObject fileObject, String projectName, String relativePath) {}

    private void openSelected() {
        if (selectionConsumer != null) {
            confirmSelection();
            return;
        }
        FileItem item = resultList.getSelectedValue();
        if (item == null) return;
        selectedItem = item;
        dispose();
        // Open in editor
        try {
            DataObject.find(item.fileObject()).getLookup()
                .lookup(OpenCookie.class)
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
                if (selectionConsumer != null) {
                    FileItem clicked = resultList.getSelectedValue();
                    if (clicked != null) {
                        resultList.clearSelection();
                        selectionConsumer.accept(List.of(clicked.fileObject()));
                        statusLabel.setText(NbBundle.getMessage(UIBundle.class, "LBL_PickerAttached"));
                        if (listModel.size() == 1) {
                            searchField.setText("");
                        }
                    }
                } else {
                    openSelected();
                }
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

    private static class FileItemRenderer extends JPanel implements ListCellRenderer<Object> {
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