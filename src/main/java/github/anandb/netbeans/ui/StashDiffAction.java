package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.netbeans.api.diff.DiffController;
import org.netbeans.api.diff.StreamSource;

import github.anandb.netbeans.support.Logger;

/**
 * Shows a side-by-side diff of a selected stash.
 * Toolbar toggles between "Diff to HEAD" and "Diff to Working Tree".
 */
@ActionID(category = "Tools", id = "github.anandb.netbeans.ui.StashDiffAction")
@ActionRegistration(displayName = "#CTL_StashDiffAction", lazy = true)
@NbBundle.Messages({
    "CTL_StashDiffAction=Diff to HEAD",
    "# {0} - stash name",
    "CTL_StashDiffAction_TopComponentName=Stash Diff ({0})",
    "CTL_StashDiffAction_DiffToHead=To HEAD",
    "CTL_StashDiffAction_DiffToWorking=To Working Tree",
    "CTL_StashDiffAction_PrevDiff=Previous difference",
    "CTL_StashDiffAction_NextDiff=Next difference"
})
public final class StashDiffAction implements java.awt.event.ActionListener {

    private static final Pattern STASH_NAME = Pattern.compile("stash@\\{(\\d+)\\}");
    /** Current diff controller for navigating differences. */
    private DiffController currentController;

    private static final Logger LOG = Logger.from(StashDiffAction.class);

    @Override
    public void actionPerformed(ActionEvent e) {
        Node node = getSelectedStashNode();
        if (node == null) {
            LOG.warn("No stash node selected in global context");
            return;
        }

        File repoDir = node.getLookup().lookup(File.class);
        if (repoDir == null) {
            LOG.warn("Selected stash node has no repository File in lookup");
            return;
        }

        String displayName = node.getDisplayName();
        LOG.info("Selected stash: displayName=\"{0}\", repo={1}", displayName, repoDir.getAbsolutePath());

        Matcher m = STASH_NAME.matcher(displayName);
        if (!m.find()) {
            LOG.warn("Display name \"{0}\" does not match stash pattern", displayName);
            return;
        }
        int stashIndex = Integer.parseInt(m.group(1));
        LOG.info("Extracted stash index: {0}", stashIndex);

        loadDiffs(repoDir, stashIndex, displayName);
    }

    private static Node getSelectedStashNode() {
        Lookup lookup = org.openide.util.Utilities.actionsGlobalContext();
        Node node = lookup.lookup(Node.class);
        if (node == null) return null;
        String name = node.getDisplayName();
        return (name != null && name.matches("stash@\\{\\d+\\}.*")) ? node : null;
    }

    // --- Git helpers ---

    private static String runGit(File dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        proc.waitFor();
        return sb.toString();
    }

    private static String stripFatal(String output) {
        return output.replaceAll("(?m)^fatal: ", "");
    }

    private static String statusName(String code) {
        if (code == null || code.isEmpty()) return "Modified";
        return switch (code.charAt(0)) {
            case 'A' -> "Added";
            case 'D' -> "Deleted";
            case 'M' -> "Modified";
            case 'R' -> "Renamed";
            case 'C' -> "Copied";
            case 'T' -> "Type changed";
            default -> code;
        };
    }

    private void loadDiffs(File repoDir, int stashIndex, String stashName) {
        CompletableFuture.supplyAsync(() -> {
            try {
                String stashRef = "stash@{" + stashIndex + "}";
                // Get file statuses from git stash show (files actually in the stash)
                String statusOutput = runGit(repoDir, "git", "stash", "show", "--name-status", "--no-renames", stashRef);
                List<String[]> fileStatusPairs = new ArrayList<>();
                for (String line : statusOutput.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    String[] parts = trimmed.split("\t", 2);
                    if (parts.length == 2) {
                        fileStatusPairs.add(new String[]{parts[0].trim(), parts[1].trim()});
                    } else if (parts.length == 1) {
                        fileStatusPairs.add(new String[]{"M", parts[0].trim()});
                    }
                }

                // For each file, get full HEAD and stash contents in parallel
                List<FileDiff> headDiffs = new ArrayList<>();
                List<FileDiff> workTreeDiffs = new ArrayList<>();
                for (String[] pair : fileStatusPairs) {
                    String status = pair[0];
                    String filePath = pair[1];
                    CompletableFuture<String> headContent = CompletableFuture.supplyAsync(() -> {
                        try { return stripFatal(runGit(repoDir, "git", "show", "HEAD:" + filePath)); }
                        catch (Exception e) { return ""; }
                    });
                    CompletableFuture<String> stashContent = CompletableFuture.supplyAsync(() -> {
                        try { return stripFatal(runGit(repoDir, "git", "show", stashRef + ":" + filePath)); }
                        catch (Exception e) { return ""; }
                    });
                    CompletableFuture<String> workTreeContent = CompletableFuture.supplyAsync(() -> {
                        try {
                            File f = new File(repoDir, filePath);
                            return f.exists() ? java.nio.file.Files.readString(f.toPath()) : "";
                        } catch (Exception e) { return ""; }
                    });

                    String h = headContent.join();
                    String s = stashContent.join();
                    String w = workTreeContent.join();
                    headDiffs.add(new FileDiff(filePath, status, h, s));
                    workTreeDiffs.add(new FileDiff(filePath, status, s, w));
                }
                return new StashDiffData(repoDir, stashIndex, stashName, headDiffs, workTreeDiffs);
            } catch (Exception ex) {
                return new StashDiffData(repoDir, stashIndex, stashName, List.of(), List.of());
            }
        }).thenAcceptAsync(data -> SwingUtilities.invokeLater(() -> openPanel(data)));
    }

    // --- UI ---

    private void openPanel(StashDiffData data) {
        if (data.headDiffs.isEmpty() && data.workTreeDiffs.isEmpty()) return;

        DefaultListModel<FileDiff> listModel = new DefaultListModel<>();
        JList<FileDiff> fileList = new JList<>(listModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setCellRenderer(new FileDiffCellRenderer());

        // Right-click context menu to apply a single file from the stash
        fileList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showFilePopup(e);
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showFilePopup(e);
            }
            private void showFilePopup(java.awt.event.MouseEvent e) {
                int idx = fileList.locationToIndex(e.getPoint());
                if (idx < 0) return;
                FileDiff fd = listModel.getElementAt(idx);
                javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
                javax.swing.JMenuItem applyItem = new javax.swing.JMenuItem("Apply this change");
                applyItem.addActionListener(ev -> {
                    try {
                        String stashRef = "stash@{" + data.stashIndex + "}";
                        String result = runGit(data.repoDir, "git", "checkout", stashRef, "--", fd.filePath);
                        LOG.info("Applied {0} from {1}: {2}", fd.filePath, stashRef, result.trim());
                    } catch (Exception ex) {
                        LOG.warn("Failed to apply {0}: {1}", fd.filePath, ex.getMessage());
                    }
                });
                popup.add(applyItem);
                popup.show(fileList, e.getX(), e.getY());
            }
        });

        JPanel diffPanel = new JPanel(new BorderLayout());

        // Toolbar with toggle buttons
        JToggleButton btnHead = new JToggleButton(Bundle.CTL_StashDiffAction_DiffToHead());
        JToggleButton btnWork = new JToggleButton(Bundle.CTL_StashDiffAction_DiffToWorking());
        ButtonGroup group = new ButtonGroup();
        group.add(btnHead);
        group.add(btnWork);
        btnHead.setSelected(false);
        btnWork.setSelected(true);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(btnHead);
        toolbar.add(btnWork);

        // Wire toggle → swap file list contents
        btnHead.addActionListener((ActionEvent ev) -> {
            switchTo(listModel, fileList, diffPanel, data.headDiffs);
        });
        btnWork.addActionListener((ActionEvent ev) -> {
            switchTo(listModel, fileList, diffPanel, data.workTreeDiffs);
        });

        // Wire file selection → diff view
        fileList.addListSelectionListener((ListSelectionEvent ev) -> {
            if (ev.getValueIsAdjusting()) return;
            FileDiff sel = fileList.getSelectedValue();
            if (sel != null) updateDiffView(diffPanel, sel);
        });

        // Layout: toolbar on top, file list left, diff right
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(toolbar, BorderLayout.NORTH);
        leftPanel.add(fileList, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, diffPanel);
        split.setDividerLocation(220);
        split.setResizeWeight(0.0);

        TopComponent tc = new TopComponent();
        tc.setLayout(new BorderLayout());
        tc.add(split, BorderLayout.CENTER);
        tc.setDisplayName(Bundle.CTL_StashDiffAction_TopComponentName(data.stashName));

        // Key bindings for navigating differences
        tc.getActionMap().put("prevDiff", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { navigateDiff(-1); }
        });
        tc.getActionMap().put("nextDiff", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { navigateDiff(1); }
        });
        tc.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.CTRL_DOWN_MASK), "prevDiff");
        tc.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, InputEvent.CTRL_DOWN_MASK), "nextDiff");

        tc.open();
        tc.requestActive();

        // Start with working tree diffs
        switchTo(listModel, fileList, diffPanel, data.workTreeDiffs);
    }

    private void switchTo(DefaultListModel<FileDiff> model, JList<FileDiff> list,
                          JPanel diffPanel, List<FileDiff> diffs) {
        Runnable task = () -> {
            model.clear();
            diffs.forEach(model::addElement);
            diffPanel.removeAll();
            diffPanel.revalidate();
            diffPanel.repaint();
            if (!diffs.isEmpty()) list.setSelectedIndex(0);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void updateDiffView(JPanel diffPanel, FileDiff fd) {
        Runnable task = () -> {
            diffPanel.removeAll();
            try {
                String name = new File(fd.filePath).getName();
                String mime = mimeFor(name);
                StreamSource base = StreamSource.createSource(
                        name + " (base)", "Base (" + statusName(fd.status) + ")", mime,
                        new java.io.StringReader(fd.headContent));
                StreamSource modified = StreamSource.createSource(
                        name + " (modified)", "Stash", mime,
                        new java.io.StringReader(fd.stashContent));
                DiffController ctrl = DiffController.createEnhanced(base, modified);
                currentController = ctrl;
                JComponent diffView = ctrl.getJComponent();

                // Wrap diff view: nav buttons above the view, aligned right
                JPanel wrapper = new JPanel(new BorderLayout());
                int navIconSize = 16;
                JPanel navBar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
                navBar.setOpaque(false);
                JButton prevBtn = new JButton(ThemeManager.getIcon("up.svg", navIconSize));
                JButton nextBtn = new JButton(ThemeManager.getIcon("down.svg", navIconSize));
                prevBtn.setMargin(new java.awt.Insets(4, 8, 4, 8));
                nextBtn.setMargin(new java.awt.Insets(4, 8, 4, 8));
                prevBtn.setToolTipText(Bundle.CTL_StashDiffAction_PrevDiff());
                nextBtn.setToolTipText(Bundle.CTL_StashDiffAction_NextDiff());
                prevBtn.addActionListener(e -> navigateDiff(-1));
                nextBtn.addActionListener(e -> navigateDiff(1));
                navBar.add(prevBtn);
                navBar.add(nextBtn);
                wrapper.add(navBar, BorderLayout.NORTH);
                wrapper.add(diffView, BorderLayout.CENTER);
                diffPanel.add(wrapper, BorderLayout.CENTER);
                // Apply font immediately and re-apply on property changes (tab switches)
                SwingUtilities.invokeLater(() -> applyDiffFont(diffView));
                ctrl.addPropertyChangeListener(evt -> {
                    if (DiffController.PROP_DIFFERENCES.equals(evt.getPropertyName())) {
                        SwingUtilities.invokeLater(() -> applyDiffFont(diffView));
                    }
                });
                // Also re-apply when component becomes visible (covers initial render)
                diffView.addHierarchyListener(e -> {
                    if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                            && diffView.isShowing()) {
                        SwingUtilities.invokeLater(() -> applyDiffFont(diffView));
                    }
                });
            } catch (Exception ex) {
                currentController = null;
                diffPanel.add(new JLabel("Error: " + ex.getMessage()), BorderLayout.CENTER);
            }
            diffPanel.revalidate();
            diffPanel.repaint();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void applyDiffFont(JComponent diffView) {
        java.awt.Font diffFont = IconResourceManager.getMonospaceFont();
        setFontRecursive(diffView, diffFont);
        diffView.revalidate();
        diffView.repaint();
    }

    private static void setFontRecursive(java.awt.Component c, java.awt.Font font) {
        if (c instanceof javax.swing.text.JTextComponent) {
            c.setFont(font);
        }
        if (c instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) c).getComponents()) {
                setFontRecursive(child, font);
            }
        }
    }

    private void navigateDiff(int direction) {
        Runnable task = () -> {
            if (currentController == null) return;
            int count = currentController.getDifferenceCount();
            if (count == 0) return;
            int current = currentController.getDifferenceIndex();
            int next = (current == -1) ? 0 : Math.min(Math.max(current + direction, 0), count - 1);
            currentController.setLocation(DiffController.DiffPane.Base,
                    DiffController.LocationType.DifferenceIndex, next);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    /** Maps file name to MIME type for syntax highlighting in the diff view. */
    private static String mimeFor(String fileName) {
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) ext = fileName.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "java"       -> "text/x-java";
            case "js", "jsx"  -> "text/javascript";
            case "ts", "tsx"  -> "text/typescript";
            case "py"         -> "text/x-python";
            case "rb"         -> "text/x-ruby";
            case "php"        -> "text/x-php";
            case "c", "h"     -> "text/x-c";
            case "cpp", "cc", "cxx", "hpp" -> "text/x-c++";
            case "cs"         -> "text/x-csharp";
            case "go"         -> "text/x-go";
            case "rs"         -> "text/x-rust";
            case "swift"      -> "text/x-swift";
            case "kt", "kts"  -> "text/x-kotlin";
            case "scala"      -> "text/x-scala";
            case "xml", "xsd", "xsl", "xslt" -> "text/xml";
            case "html", "htm", "xhtml" -> "text/html";
            case "css"        -> "text/css";
            case "scss", "sass" -> "text/x-scss";
            case "less"       -> "text/x-less";
            case "json"       -> "application/json";
            case "yaml", "yml" -> "text/x-yaml";
            case "toml"       -> "text/x-toml";
            case "sh", "bash", "zsh" -> "text/x-shellscript";
            case "sql"        -> "text/x-sql";
            case "md", "markdown" -> "text/x-markdown";
            case "properties" -> "text/x-properties";
            case "gradle"     -> "text/x-gradle";
            case "groovy", "gvy", "gy", "gsh" -> "text/x-groovy";
            case "lua"        -> "text/x-lua";
            case "r"          -> "text/x-r";
            case "pl", "pm"   -> "text/x-perl";
            case "vim"        -> "text/x-vim";
            case "diff", "patch" -> "text/x-diff";
            case "svg"        -> "text/xml";
            case "ini", "cfg", "conf" -> "text/x-ini";
            default           -> "text/plain";
        };
    }

    // --- Inner types ---

    private static class FileDiffCellRenderer extends DefaultListCellRenderer {
        private static final javax.swing.Icon FILE_ICON = ThemeManager.getIcon("file.svg", 16);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof FileDiff fd) {
                java.awt.Font mono = getFont().deriveFont(java.awt.Font.PLAIN);
                setFont(mono);
                setIcon(FILE_ICON);
                setIconTextGap(8);
                setText(new File(fd.filePath).getName());
                setToolTipText(fd.filePath);
                setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 4, 3, 4));
            }
            return this;
        }
    }

    private static class FileDiff {
        final String filePath;
        final String status;
        final String headContent;
        final String stashContent;

        FileDiff(String filePath, String status, String headContent, String stashContent) {
            this.filePath = filePath;
            this.status = status;
            this.headContent = headContent;
            this.stashContent = stashContent;
        }
    }

    private static class StashDiffData {
        final File repoDir;
        final int stashIndex;
        final String stashName;
        final List<FileDiff> headDiffs;
        final List<FileDiff> workTreeDiffs;

        StashDiffData(File repoDir, int stashIndex, String stashName,
                      List<FileDiff> headDiffs, List<FileDiff> workTreeDiffs) {
            this.repoDir = repoDir;
            this.stashIndex = stashIndex;
            this.stashName = stashName;
            this.headDiffs = headDiffs;
            this.workTreeDiffs = workTreeDiffs;
        }
    }
}
