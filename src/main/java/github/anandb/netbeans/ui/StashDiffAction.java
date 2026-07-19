package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.prefs.PreferenceChangeListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.text.JTextComponent;
import org.netbeans.api.diff.DiffController;
import org.netbeans.api.diff.StreamSource;
import org.openide.awt.ActionID;
import org.openide.nodes.Node;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.openide.util.lookup.ServiceProvider;

import github.anandb.netbeans.contract.StashDiffControl;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.PreferenceKeys;
import org.openide.util.NbPreferences;

/**
 * Shows a side-by-side diff of a selected stash.
 * Toolbar toggles between "Diff to HEAD" and "Diff to Working Tree".
 */
@ActionID(category = "Tools", id = "github.anandb.netbeans.ui.StashDiffAction")
@ServiceProvider(service = StashDiffControl.class)
@NbBundle.Messages({
    "CTL_StashDiffAction=Diff Stash",
    "# {0} - stash name",
    "CTL_StashDiffAction_TopComponentName=Stash Diff ({0})",
    "CTL_StashDiffAction_DiffToBase=To Base",
    "CTL_StashDiffAction_DiffToHead=To HEAD",
    "CTL_StashDiffAction_DiffToWorking=To Working Tree",
    "CTL_StashDiffAction_Tip=<html>Diff a selected stash.<br>Select a stash in the Git Repository Browser first.</html>",
    "CTL_StashDiffAction_DisabledTip=Stash Diff is disabled. Enable in Assistant Settings.",
    "CTL_StashDiffAction_PrevDiff=Previous difference",
    "CTL_StashDiffAction_NextDiff=Next difference"
})
public final class StashDiffAction extends AbstractAction implements Presenter.Toolbar, StashDiffControl {

    private static final Pattern STASH_NAME = Pattern.compile("stash@\\{(\\d+)\\}");
    private static final Pattern STASH_REF_PATTERN = Pattern.compile("stash@\\{\\d+\\}.*");
    private static final Pattern FATAL_PREFIX = Pattern.compile("(?m)^fatal: ");
    /** Current diff controller for navigating differences. */
    private DiffController currentController;
    private PropertyChangeListener currentDiffListener;

    private static final Logger LOG = Logger.from(StashDiffAction.class);

    // --- Toolbar presenter ---

    @Override
    public Component getToolbarPresenter() {
        class ToolbarButton extends JButton implements PropertyChangeListener {
            private static final long serialVersionUID = 1L;
            private final PreferenceChangeListener prefListener = evt -> {
                if (PreferenceKeys.ACTIONS_STASH_DIFF.equals(evt.getKey())) {
                    updateState();
                }
            };

            ToolbarButton() {
                Icon enabledIcon = ThemeManager.getIcon("stash.png", PluginSettings.getToolbarIconSize());
                setIcon(enabledIcon);
                if (enabledIcon != null) {
                    setDisabledIcon(ImageUtilities.createDisabledIcon(enabledIcon));
                }
                setToolTipText(Bundle.CTL_StashDiffAction_Tip());
                getAccessibleContext().setAccessibleName(Bundle.CTL_StashDiffAction_Tip());
                getAccessibleContext().setAccessibleDescription(Bundle.CTL_StashDiffAction_Tip());
                UIUtils.styleToolbarButton(this);
                addActionListener(StashDiffAction.this);
            }

            @Override
            public Point getToolTipLocation(MouseEvent event) {
                Insets ins = getInsets();
                return new Point(ins.left, -getHeight() - 8);
            }

            @Override
            public void addNotify() {
                super.addNotify();
                updateState();
                TopComponent.getRegistry().addPropertyChangeListener(this);
                NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).addPreferenceChangeListener(prefListener);
            }

            @Override
            public void removeNotify() {
                TopComponent.getRegistry().removePropertyChangeListener(this);
                NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).removePreferenceChangeListener(prefListener);
                super.removeNotify();
            }

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (TopComponent.Registry.PROP_OPENED.equals(evt.getPropertyName())) {
                    updateState();
                }
            }

            private void updateState() {
                boolean enabled = isGitRepositoriesOpen() && PluginSettings.isStashDiffEnabled();
                setEnabled(enabled);
                StashDiffAction.this.setEnabled(enabled);
                String tip = enabled ? Bundle.CTL_StashDiffAction_Tip()
                        : Bundle.CTL_StashDiffAction_DisabledTip();
                setToolTipText(tip);
                getAccessibleContext().setAccessibleName(tip);
                getAccessibleContext().setAccessibleDescription(tip);
            }
        }
        return new ToolbarButton();
    }

    private static boolean isGitRepositoriesOpen() {
        TopComponent tc = WindowManager.getDefault().findTopComponent("GitRepositories");
        return tc != null && tc.isOpened();
    }

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
        Lookup lookup = Utilities.actionsGlobalContext();
        Node node = lookup.lookup(Node.class);
        if (node == null) return null;
        String name = node.getDisplayName();
        return (name != null && STASH_REF_PATTERN.matcher(name).matches()) ? node : null;
    }

    // --- Git helpers ---

    private static final RequestProcessor GIT_RP = new RequestProcessor("StashDiff-git", 1);
    private static final RequestProcessor READER_RP = new RequestProcessor("StashDiff-reader", 2);
    /** Dedicated executor for loadDiffs to avoid starving ForkJoinPool.commonPool. */
    private static final ExecutorService LOAD_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "StashDiff-load");
        t.setDaemon(true);
        return t;
    });

    private static String runGit(File dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        // Use StringBuffer (thread-safe) — reader appends on READER_RP, caller reads after waitFinished.
        StringBuffer sb = new StringBuffer();
        RequestProcessor.Task readerTask = READER_RP.post(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            } catch (IOException e) {
                // Expected when process is destroyed before all output is read
            }
        });
        try {
            boolean timedOut = !proc.waitFor(60, TimeUnit.SECONDS);
            if (timedOut) {
                proc.destroyForcibly();
            }
            // Always wait for reader before reading sb (destroyForcibly above closes stdout,
            // unblocking readLine). Use 5s timeout to avoid data race on large output.
            readerTask.waitFinished(5000);
            if (timedOut) {
                throw new RuntimeException("git command timed out after 60 seconds: " + String.join(" ", cmd));
            }
            return sb.toString();
        } finally {
            if (proc.isAlive()) {
                proc.destroyForcibly();
            }
            readerTask.waitFinished(5000);
        }
    }

    private static String stripFatal(String output) {
        return FATAL_PREFIX.matcher(output).replaceAll("");
    }

    /** Simulate a 3-way merge in-memory using git merge-file. Returns merged content. */
    private static String threeWayMerge(File repoDir, String base, String ours, String theirs) {
        // Trivial cases: file added or deleted in stash
        if (theirs == null || theirs.isEmpty()) return ours != null ? ours : "";
        if ((base == null || base.isEmpty()) && (ours == null || ours.isEmpty())) return theirs;
        File baseF = null;
        File oursF = null;
        File theirsF = null;
        try {
            baseF = File.createTempFile("gmerge-base-", ".tmp");
            oursF = File.createTempFile("gmerge-ours-", ".tmp");
            theirsF = File.createTempFile("gmerge-theirs-", ".tmp");
            Files.writeString(baseF.toPath(), base == null ? "" : base);
            Files.writeString(oursF.toPath(), ours == null ? "" : ours);
            Files.writeString(theirsF.toPath(), theirs == null ? "" : theirs);
            return runGit(repoDir, "git", "merge-file", "-p",
                    oursF.getAbsolutePath(), baseF.getAbsolutePath(), theirsF.getAbsolutePath());
        } catch (Exception e) {
            return theirs;
        } finally {
            deleteTempFile(baseF);
            deleteTempFile(oursF);
            deleteTempFile(theirsF);
        }
    }

    /** True if merge output contains standard conflict markers. */
    private static boolean hasConflictMarkers(String content) {
        return content.contains("<<<<<<<") || content.contains("=======") || content.contains(">>>>>>>");
    }

    private static void deleteTempFile(File f) {
        if (f == null) return;
        try {
            Files.deleteIfExists(f.toPath());
        } catch (IOException ignored) {
            // Temp file cleanup is best-effort
        }
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

    /**
     * Opens the stash diff viewer for a given stash index in the specified repository.
     * Called via {@link StashDiffControl} from non-UI layers (e.g. MCP tools).
     */
    @Override
    public void openStashDiff(File repoDir, int stashIndex) {
        String stashName = "stash@{" + stashIndex + "}";
        loadDiffs(repoDir, stashIndex, stashName);
    }

    @Override
    public String validateStash(File repoDir, int stashIndex) {
        try {
            String stashRef = "stash@{" + stashIndex + "}";
            String result = stripFatal(runGit(repoDir, "git", "rev-parse", "--verify", stashRef)).trim();
            if (result.isEmpty()) {
                return "Stash " + stashRef + " does not exist";
            }
            return null; // valid
        } catch (Exception e) {
            String msg = e.getMessage();
            return msg != null ? msg : "Failed to validate stash";
        }
    }

    private void loadDiffs(File repoDir, int stashIndex, String stashName) {
        CompletableFuture.supplyAsync(() -> {
            try {
                String stashRef = "stash@{" + stashIndex + "}";
                String baseRef = stashRef + "^";
                String baseShortHash = stripFatal(runGit(repoDir, "git", "rev-parse", "--short", baseRef)).trim();
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

                // For each file, get full HEAD, stash, base (stash parent), and working tree contents in parallel
                List<FileDiff> headDiffs = new ArrayList<>();
                List<FileDiff> workTreeDiffs = new ArrayList<>();
                List<FileDiff> baseDiffs = new ArrayList<>();
                for (String[] pair : fileStatusPairs) {
                    String status = pair[0];
                    String filePath = pair[1];
                    CompletableFuture<String> headContent = CompletableFuture.supplyAsync(() -> {
                        try { return stripFatal(runGit(repoDir, "git", "show", "HEAD:" + filePath)); }
                        catch (Exception e) {
                            LOG.warn("Failed HEAD content for {0}: {1}", filePath, e.getMessage(), e);
                            return "";
                        }
                    }, LOAD_EXECUTOR);
                    CompletableFuture<String> stashContent = CompletableFuture.supplyAsync(() -> {
                        try { return stripFatal(runGit(repoDir, "git", "show", stashRef + ":" + filePath)); }
                        catch (Exception e) {
                            LOG.warn("Failed stash content for {0}: {1}", filePath, e.getMessage(), e);
                            return "";
                        }
                    }, LOAD_EXECUTOR);
                    CompletableFuture<String> baseContent = CompletableFuture.supplyAsync(() -> {
                        try { return stripFatal(runGit(repoDir, "git", "show", stashRef + "^:" + filePath)); }
                        catch (Exception e) {
                            LOG.warn("Failed base content for {0}: {1}", filePath, e.getMessage(), e);
                            return "";
                        }
                    }, LOAD_EXECUTOR);
                    CompletableFuture<String> workTreeContent = CompletableFuture.supplyAsync(() -> {
                        try {
                            File f = new File(repoDir, filePath);
                            return f.exists() ? Files.readString(f.toPath()) : "";
                        } catch (Exception e) {
                            LOG.warn("Failed work-tree content for {0}: {1}", filePath, e.getMessage(), e);
                            return "";
                        }
                    }, LOAD_EXECUTOR);

                    String h = headContent.join();
                    String s = stashContent.join();
                    String b = baseContent.join();
                    String w = workTreeContent.join();
                    // Simulate applying stash to HEAD and to working tree via 3-way merge
                    String headMerge = threeWayMerge(repoDir, b, h, s);
                    boolean headConflict = hasConflictMarkers(headMerge);
                    String workTreeMerge = threeWayMerge(repoDir, b, w, s);
                    boolean workTreeConflict = hasConflictMarkers(workTreeMerge);
                    headDiffs.add(new FileDiff(filePath, status, h, headMerge, headConflict, "HEAD"));
                    // Working tree status: real uncommitted changes, not stash status
                    String workTreeLabel = w.equals(h) ? "Unchanged in tree" : "Modified in tree";
                    workTreeDiffs.add(new FileDiff(filePath, status, w, workTreeMerge, workTreeConflict, workTreeLabel));
                    baseDiffs.add(new FileDiff(filePath, status, b, s, false, "Base (" + baseShortHash + ")"));
                }
                return new StashDiffData(repoDir, stashIndex, stashName, headDiffs, workTreeDiffs, baseDiffs);
            } catch (Exception ex) {
                return new StashDiffData(repoDir, stashIndex, stashName, List.of(), List.of(), List.of());
            }
        }, LOAD_EXECUTOR).thenAcceptAsync(data -> SwingUtilities.invokeLater(() -> openPanel(data)));
    }

    // --- UI ---

    private void openPanel(StashDiffData data) {
        if (data.headDiffs.isEmpty() && data.workTreeDiffs.isEmpty() && data.baseDiffs.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Stash " + data.stashName + " has no changes or could not be loaded.",
                    "Stash Diff", JOptionPane.WARNING_MESSAGE);
            return;
        }

        DefaultListModel<FileDiff> listModel = new DefaultListModel<>();
        JList<FileDiff> fileList = new JList<>(listModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setCellRenderer(new FileDiffCellRenderer());

        // Right-click context menu to apply a single file from the stash
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showFilePopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showFilePopup(e);
            }
            private void showFilePopup(MouseEvent e) {
                int idx = fileList.locationToIndex(e.getPoint());
                if (idx < 0) return;
                FileDiff fd = listModel.getElementAt(idx);
                JPopupMenu popup = new JPopupMenu();
                JMenuItem applyItem = new JMenuItem("Apply this change");
                applyItem.addActionListener(ev -> {
                    applyItem.setEnabled(false);
                    GIT_RP.post(() -> {
                        try {
                            String stashRef = "stash@{" + data.stashIndex + "}";
                            runGit(data.repoDir, "git", "checkout", stashRef, "--", fd.filePath);
                            LOG.info("Applied {0} from {1}", fd.filePath, stashRef);
                        } catch (Exception ex) {
                            LOG.warn("Failed to apply {0}: {1}", fd.filePath, ex.getMessage());
                        } finally {
                            SwingUtilities.invokeLater(() -> applyItem.setEnabled(true));
                        }
                    });
                });
                popup.add(applyItem);
                popup.show(fileList, e.getX(), e.getY());
            }
        });

        JPanel diffPanel = new JPanel(new BorderLayout());

        // Toolbar with toggle buttons
        JToggleButton btnBase = new JToggleButton(Bundle.CTL_StashDiffAction_DiffToBase());
        JToggleButton btnHead = new JToggleButton(Bundle.CTL_StashDiffAction_DiffToHead());
        JToggleButton btnWork = new JToggleButton(Bundle.CTL_StashDiffAction_DiffToWorking());
        ButtonGroup group = new ButtonGroup();
        group.add(btnBase);
        group.add(btnHead);
        group.add(btnWork);
        btnBase.setSelected(true);
        btnHead.setSelected(false);
        btnWork.setSelected(false);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(btnBase);
        toolbar.add(btnHead);
        toolbar.add(btnWork);

        // --- Stash lifecycle buttons (use holder array for tc reference) ---
        final TopComponent[] tcRef = new TopComponent[1];
        toolbar.add(Box.createHorizontalStrut(32));

        JButton btnDropStash = new JButton("Drop", ThemeManager.getIcon("stash_drop.svg", PluginSettings.getToolbarIconSize()));
        JButton btnApplyStash = new JButton("Apply", ThemeManager.getIcon("stash_apply.svg", PluginSettings.getToolbarIconSize()));
        btnApplyStash.setHorizontalTextPosition(SwingConstants.RIGHT);
        btnApplyStash.setToolTipText("Apply this stash to the working tree");
        btnApplyStash.addActionListener(ev -> {
            btnApplyStash.setEnabled(false);
            btnDropStash.setEnabled(false);
            GIT_RP.post(() -> {
                try {
                    String stashRef = "stash@{" + data.stashIndex + "}";
                    runGit(data.repoDir, "git", "stash", "apply", stashRef);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(tcRef[0],
                                "Stash applied successfully.", "Apply Stash",
                                JOptionPane.INFORMATION_MESSAGE);
                        btnApplyStash.setEnabled(true);
                        btnDropStash.setEnabled(true);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        LOG.warn("Failed to apply stash: {0}", ex.getMessage());
                        showStashError(tcRef[0], ex);
                        btnApplyStash.setEnabled(true);
                        btnDropStash.setEnabled(true);
                    });
                }
            });
        });

        btnDropStash.setHorizontalTextPosition(SwingConstants.RIGHT);
        btnDropStash.setToolTipText("Drop this stash permanently");
        btnDropStash.addActionListener(ev -> {
            int confirm = JOptionPane.showConfirmDialog(tcRef[0],
                    "Are you sure you want to drop stash@{" + data.stashIndex + "}?",
                    "Drop Stash", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            btnApplyStash.setEnabled(false);
            btnDropStash.setEnabled(false);
            GIT_RP.post(() -> {
                try {
                    String stashRef = "stash@{" + data.stashIndex + "}";
                    runGit(data.repoDir, "git", "stash", "drop", stashRef);
                    SwingUtilities.invokeLater(() -> {
                        if (tcRef[0] != null) tcRef[0].close();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        LOG.warn("Failed to drop stash: {0}", ex.getMessage());
                        showStashError(tcRef[0], ex);
                        btnApplyStash.setEnabled(true);
                        btnDropStash.setEnabled(true);
                    });
                }
            });
        });

        toolbar.add(btnApplyStash);
        toolbar.add(btnDropStash);

        // Wire toggle → swap file list contents
        btnBase.addActionListener((ActionEvent ev) -> {
            switchTo(listModel, fileList, diffPanel, data.baseDiffs);
        });
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
        // Size left panel to fit widest content: toolbar total width + file names
        Font uiFont = fileList.getFont();
        if (uiFont == null) uiFont = UIManager.getFont("Label.font");
        FontMetrics fm = fileList.getFontMetrics(uiFont);
        int widestFileName = data.headDiffs.stream()
                .mapToInt(fd -> fm.stringWidth(fd.filePath))
                .max().orElse(0);
        toolbar.doLayout();
        int toolbarWidth = toolbar.getPreferredSize().width;
        split.setDividerLocation(Math.max(widestFileName + 50, Math.max(toolbarWidth, 220)));
        split.setResizeWeight(0.0);

        TopComponent tc = new TopComponent();
        tcRef[0] = tc;
        tc.setLayout(new BorderLayout());
        tc.add(split, BorderLayout.CENTER);
        tc.setDisplayName(Bundle.CTL_StashDiffAction_TopComponentName(data.stashName));
        tc.putClientProperty("PersistenceType", "Never");

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

        // Start with base diffs
        switchTo(listModel, fileList, diffPanel, data.baseDiffs);
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
                String baseTitle = fd.leftLabel != null
                        ? fd.leftLabel
                        : "Base (" + statusName(fd.status) + ")";
                StreamSource base = StreamSource.createSource(
                        name + " (base)", baseTitle, mime,
                        new StringReader(fd.headContent));
                String modifiedTitle = fd.conflict
                        ? "<html>Stash (<font color='red'>Conflict</font>)</html>"
                        : "Stash";
                StreamSource modified = StreamSource.createSource(
                        name + " (modified)", modifiedTitle, mime,
                        new StringReader(fd.stashContent));
                DiffController ctrl = DiffController.createEnhanced(base, modified);
                JComponent diffView = ctrl.getJComponent();
                // Remove old listener before replacing controller
                if (currentController != null && currentDiffListener != null) {
                    currentController.removePropertyChangeListener(currentDiffListener);
                }
                currentDiffListener = evt -> {
                    if (DiffController.PROP_DIFFERENCES.equals(evt.getPropertyName())) {
                        SwingUtilities.invokeLater(() -> applyDiffFont(diffView));
                    }
                };
                ctrl.addPropertyChangeListener(currentDiffListener);
                currentController = ctrl;

                // Wrap diff view: nav buttons above the view, aligned left
                JPanel wrapper = new JPanel(new BorderLayout());
                int navIconSize = 16;
                JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                navBar.setOpaque(false);
                JButton prevBtn = new JButton(ThemeManager.getIcon("up.svg", navIconSize));
                JButton nextBtn = new JButton(ThemeManager.getIcon("down.svg", navIconSize));
                prevBtn.setMargin(new Insets(4, 8, 4, 8));
                nextBtn.setMargin(new Insets(4, 8, 4, 8));
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
                // Also re-apply when component becomes visible (covers initial render)
                diffView.addHierarchyListener(e -> {
                    if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
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
        Font diffFont = IconResourceManager.getMonospaceFont();
        setFontRecursive(diffView, diffFont);
        diffView.revalidate();
        diffView.repaint();
    }

    private static void setFontRecursive(Component c, Font font) {
        if (c instanceof JTextComponent) {
            c.setFont(font);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
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

    /** Show a user-friendly error dialog. */
    private static void showStashError(Component parent, Exception ex) {
        String msg = ex.getMessage();
        JOptionPane.showMessageDialog(parent,
                msg != null ? msg : "Unknown error",
                "Stash Operation Failed", JOptionPane.ERROR_MESSAGE);
    }

    // --- Inner types ---

    private static class FileDiffCellRenderer extends DefaultListCellRenderer {
        private static final Icon FILE_ICON = ThemeManager.getIcon("file.svg", 16);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof FileDiff fd) {
                Font mono = getFont().deriveFont(Font.PLAIN);
                setFont(mono);
                setIcon(FILE_ICON);
                setIconTextGap(8);
                setText(new File(fd.filePath).getName());
                setToolTipText(fd.filePath);
                setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            }
            return this;
        }
    }

    private static class FileDiff {
        final String filePath;
        final String status;
        final String headContent;
        final String stashContent;
        final boolean conflict;
        final String leftLabel; // null = use default "Base (status)"

        FileDiff(String filePath, String status, String headContent, String stashContent) {
            this(filePath, status, headContent, stashContent, false, null);
        }

        FileDiff(String filePath, String status, String headContent, String stashContent, boolean conflict) {
            this(filePath, status, headContent, stashContent, conflict, null);
        }

        FileDiff(String filePath, String status, String headContent, String stashContent, boolean conflict, String leftLabel) {
            this.filePath = filePath;
            this.status = status;
            this.headContent = headContent;
            this.stashContent = stashContent;
            this.conflict = conflict;
            this.leftLabel = leftLabel;
        }
    }

    private static class StashDiffData {
        final File repoDir;
        final int stashIndex;
        final String stashName;
        final List<FileDiff> headDiffs;
        final List<FileDiff> workTreeDiffs;
        final List<FileDiff> baseDiffs;

        StashDiffData(File repoDir, int stashIndex, String stashName,
                      List<FileDiff> headDiffs, List<FileDiff> workTreeDiffs,
                      List<FileDiff> baseDiffs) {
            this.repoDir = repoDir;
            this.stashIndex = stashIndex;
            this.stashName = stashName;
            this.headDiffs = headDiffs;
            this.workTreeDiffs = workTreeDiffs;
            this.baseDiffs = baseDiffs;
        }
    }
}
