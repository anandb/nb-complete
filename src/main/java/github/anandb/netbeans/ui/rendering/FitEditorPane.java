package github.anandb.netbeans.ui.rendering;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.StringReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.TextUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.View;

import org.openide.cookies.OpenCookie;
import org.openide.cookies.LineCookie;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.RequestProcessor;
import org.openide.util.NbBundle;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.awt.HtmlBrowser;
import org.openide.util.Lookup;
import org.netbeans.api.project.Project;
import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.ui.UIBundle;

// DSL-LEAF: keep imperative, wrap via UI.of(...) — HTML Document reuse via
// doc.remove()+kit.read(); non-declarative layout-tree-avoidance logic.
// Not ported to withStyle.
public class FitEditorPane extends JTextPane {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.from(FitEditorPane.class);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final RequestProcessor LINK_RP = new RequestProcessor("chat-file-link", 1, true);

    private volatile int lastComputedHeight = 0;
    private volatile int lastComputedWidth = 0;
    private String lastText = null;
    private volatile Dimension cachedSize = null;    
    private volatile boolean suppressRevalidate = false;
    /**
     * Cached width from the parent tree walk in getPreferredSize(). 
     * Avoids re-walking the component hierarchy on every layout pass when the component
     * has not yet been assigned a real width ({@code getWidth() <= 0}).
     */
    private int cachedParentWidth = -1;
    private int cachedParentWidthFor = -1; // the getWidth() value when cached

    @Override
    public void revalidate() {
        if (suppressRevalidate) {
            return;
        }
        super.revalidate();
    }

    @Override
    public void setText(String t) {
        if (t != null && t.equals(lastText)) {
            return;
        }
        lastText = t;

        // Reuse the existing Document so the HTML view tree is updated
        // in-place rather than torn down and rebuilt from scratch.
        // JTextComponent.setText always obtains the EditorKit again,
        // opens a new StringReader, and dispatches separate remove +
        // insert DocumentEvents.  This path collapses those steps into
        // a single bulk operation on the existing document.
        Document doc = getDocument();
        if (doc == null) {
            super.setText(t);
        } else {
            try {
                if (doc.getLength() > 0) {
                    doc.remove(0, doc.getLength());
                }
                if (t != null && !t.isEmpty()) {
                    getEditorKit().read(new StringReader(t), doc, 0);
                }
            } catch (BadLocationException | IOException ex) {
                super.setText(t);
            }
        }

        lastComputedHeight = 0;
        cachedSize = null;
    }

    @Override
    public Dimension getPreferredSize() {
        suppressRevalidate = true;
        try {
            Insets insets = getInsets();

            int w = getWidth();
            if (w <= 0) {
                // Cache the parent tree walk result to avoid re-walking on
                // every layout pass while the component is still unsized.
                int currentW = getWidth();
                if (currentW == cachedParentWidthFor && cachedParentWidth > 0) {
                    w = cachedParentWidth;
                } else {
                    Component p = getParent();
                    while (p != null) {
                        if (p.getWidth() > 0) {
                            w = p.getWidth();
                            break;
                        }
                        p = p.getParent();
                    }
                    cachedParentWidth = w;
                    cachedParentWidthFor = currentW;
                }
            }

            if (w <= 0) {
                w = 500;
            }

            if (w == lastComputedWidth && lastComputedHeight > 0 && cachedSize != null) {
                return cachedSize;
            }

            try {
                View root = getUI().getRootView(this);
                if (root != null) {
                    int contentWidth = Math.max(1, w - insets.left - insets.right);
                    root.setSize(contentWidth, Integer.MAX_VALUE);

                    float h = root.getPreferredSpan(View.Y_AXIS);
                    if (h > 0) {
                        lastComputedHeight = (int) Math.ceil(h);
                        lastComputedWidth = w;
                        // +10 fudge prevents last text line from being clipped
                        // (macOS font descenders can be larger; +5 was insufficient)
                        cachedSize = new Dimension(w, lastComputedHeight + insets.top + insets.bottom + 10);
                        return cachedSize;
                    }
                }
            } catch (Exception ex) {
                LOG.fine("View sizing failed, using fallback: {0}", ExceptionUtils.getMessage(ex));
            }

            if (lastComputedHeight > 0) {
                // +10 fudge prevents last text line from being clipped
                // (macOS font descenders can be larger; +5 was insufficient)
                cachedSize = new Dimension(w, Math.max(30, lastComputedHeight + insets.top + insets.bottom + 10));
                return cachedSize;
            }
            Dimension superSize = super.getPreferredSize();
            cachedSize = new Dimension(w, Math.max(30, superSize.height + insets.top + insets.bottom + 2));
            return cachedSize;
        } finally {
            suppressRevalidate = false;
        }
    }

    @Override
    public void doLayout() {
        try {
            super.doLayout();
        } catch (ArrayIndexOutOfBoundsException e) {
            // JDK bug: BoxView.getOffset() assumes non-empty view children
            // during HTML layout when the view tree hasn't been built yet.
            // Safe to ignore — layout will succeed on the next pass.
        }
    }

    @Override
    public float getAlignmentX() {
        return Component.LEFT_ALIGNMENT;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width > 0 && width != lastComputedWidth;
        super.setBounds(x, y, width, height);
        if (widthChanged) {
            lastComputedWidth = width;
            cachedSize = null;
            // Force the HTML view to reformat at the new width without re-queuing revalidate on EDT.
            suppressRevalidate = true;
            try {
                TextUI ui = (TextUI) getUI();
                if (ui != null) {
                    View root = ui.getRootView(this);
                    if (root != null) {
                        Insets ins = getInsets();
                        int cw = Math.max(1, width - ins.left - ins.right);
                        root.setSize(cw, Integer.MAX_VALUE);
                    }
                }
            } catch (Exception ex) {
                LOG.fine("Failed to format HTML view in setBounds: {0}", ExceptionUtils.getMessage(ex));
            } finally {
                suppressRevalidate = false;
            }
        }
    }

    private volatile Dimension cachedMaxSize;

    @Override
    public Dimension getMaximumSize() {
        // No artificial width cap: BoxLayout(Y_AXIS) may stretch the pane to the width of
        // the chat panel. A monitor-derived column width used to clip wide content (a table
        // cannot shrink its columns past their minimum) and made sidebar resizes do nothing.
        // Height still follows the preferred size, so the assigned width and the height
        // computed for it stay in agreement.
        Dimension pref = getPreferredSize();
        if (cachedMaxSize == null) {
            cachedMaxSize = new Dimension(Integer.MAX_VALUE, pref.height);
        } else {
            cachedMaxSize.height = pref.height;
        }
        return cachedMaxSize;
    }

    public static FitEditorPane createHtmlPane(String styledHtml, Color bg, String role, boolean opaqueUser) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        FitEditorPane pane = new FitEditorPane();
        pane.putClientProperty(JTextPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setEditable(false);
        pane.setContentType("text/html");
        if (opaqueUser && "user".equals(role) && bg != null && bg.getAlpha() > 0) {
            pane.setOpaque(true);
            pane.setBackground(bg);
        } else {
            pane.setOpaque(false);
            pane.setBackground(TRANSPARENT);
        }
        pane.setMargin(new Insets(0, 0, 0, 0));
        pane.setForeground(theme.foreground());
        pane.setDoubleBuffered(true);

        // Bottom padding extra generous to prevent last line from being clipped
        pane.setBorder(new EmptyBorder(4, 20, 8, 6));
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        pane.setAlignmentY(Component.CENTER_ALIGNMENT);
        pane.addHyperlinkListener(event -> {
            if (event.getEventType() != javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                return;
            }
            URL url = event.getURL();
            LocalFileLink link = localFileForLink(event.getDescription(), url);
            if (link != null) {
                LINK_RP.post(() -> openLocalFile(link));
            } else if (url != null && isWebUrl(url)) {
                HtmlBrowser.URLDisplayer.getDefault().showURL(url);
            }
        });
        pane.setText(styledHtml);
        return pane;
    }

    private record LocalFileLink(File file, Integer line) {
    }

    private record LinkPath(String path, Integer line) {
    }

    private static LocalFileLink localFileForLink(String description, URL url) {
        try {
            if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
                return localFileFromUri(url.toURI());
            }
            if (description == null) {
                return null;
            }
            if (description.startsWith("/")) {
                try {
                    URI uri = URI.create(description);
                    return localFileFromPath(uri.getPath(), uri.getFragment());
                } catch (IllegalArgumentException ex) {
                    return localFileFromPath(description, null);
                }
            }
            File path = new File(description);
            if (path.isAbsolute()) {
                return localFileFromPath(description, null);
            }
            URI uri = URI.create(description);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return localFileFromUri(uri);
            }
            return null;
        } catch (Exception ex) {
            LOG.fine("Unable to resolve chat hyperlink: {0}", ExceptionUtils.getMessage(ex));
            return null;
        }
    }

    private static LocalFileLink localFileFromUri(URI uri) throws java.net.URISyntaxException {
        LinkPath linkPath = stripLineSuffix(uri.getPath(), uri.getFragment());
        URI fileUri = new URI(uri.getScheme(), uri.getAuthority(), linkPath.path(), uri.getQuery(), null);
        return new LocalFileLink(new File(fileUri), linkPath.line());
    }

    private static LocalFileLink localFileFromPath(String path, String fragment) {
        LinkPath linkPath = stripLineSuffix(path, fragment);
        return new LocalFileLink(Path.of(linkPath.path()).toFile(), linkPath.line());
    }

    private static LinkPath stripLineSuffix(String path, String fragment) {
        Integer line = lineFromFragment(fragment);
        String filePath = path;
        int colon = path.lastIndexOf(':');
        if (colon > 0 && colon < path.length() - 1 && hasOnlyDigits(path, colon + 1)) {
            Integer suffixLine = positiveLine(path, colon + 1, path.length());
            if (suffixLine != null) {
                line = suffixLine;
                filePath = path.substring(0, colon);
            }
        }
        return new LinkPath(filePath, line);
    }

    private static Integer lineFromFragment(String fragment) {
        if (fragment == null || fragment.length() < 2
                || (fragment.charAt(0) != 'L' && fragment.charAt(0) != 'l')
                || !hasOnlyDigits(fragment, 1)) {
            return null;
        }
        return positiveLine(fragment, 1, fragment.length());
    }

    private static boolean hasOnlyDigits(String value, int start) {
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static Integer positiveLine(String value, int start, int end) {
        try {
            int line = Integer.parseInt(value.substring(start, end));
            return line > 0 ? line : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isWebUrl(URL url) {
        return "http".equalsIgnoreCase(url.getProtocol()) || "https".equalsIgnoreCase(url.getProtocol());
    }

    private static void openLocalFile(LocalFileLink link) {
        try {
            File canonical = link.file().getCanonicalFile();
            if (!isInOpenProject(canonical)) {
                LOG.fine("Chat file link is outside the open projects: {0}", canonical);
                return;
            }
            Path normalized = FileUtil.normalizeFile(canonical).toPath();
            FileObject fileObject = FileUtil.toFileObject(normalized.toFile());
            if (fileObject == null || fileObject.isFolder()) {
                LOG.fine("Chat file link does not resolve to a file: {0}", normalized);
                return;
            }
            DataObject dataObject = DataObject.find(fileObject);
            if (link.line() != null) {
                LineCookie lineCookie = dataObject.getLookup().lookup(LineCookie.class);
                if (lineCookie != null) {
                    Line line = lineCookie.getLineSet().getOriginal(link.line() - 1);
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        try {
                            line.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.NONE);
                        } catch (Exception ex) {
                            LOG.warn("Failed to navigate to chat file link line: {0}",
                                    ExceptionUtils.getMessage(ex));
                        }
                    });
                    return;
                }
            }
            OpenCookie openCookie = dataObject.getLookup().lookup(OpenCookie.class);
            if (openCookie == null) {
                LOG.fine("No editor opener is registered for chat file link: {0}", normalized);
                return;
            }
            javax.swing.SwingUtilities.invokeLater(openCookie::open);
        } catch (Exception ex) {
            LOG.warn("Failed to open chat file link: {0}", ExceptionUtils.getMessage(ex));
        }
    }

    private static boolean isInOpenProject(File file) throws IOException {
        ProjectQuery projectQuery = Lookup.getDefault().lookup(ProjectQuery.class);
        Project[] projects = projectQuery == null ? new Project[0] : projectQuery.getAllOpenProjects();
        String target = file.getCanonicalPath();
        for (Project project : projects) {
            File projectDirectory = FileUtil.toFile(project.getProjectDirectory());
            if (projectDirectory == null) {
                continue;
            }
            String root = projectDirectory.getCanonicalPath();
            if (target.equals(root) || target.startsWith(root + File.separator)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public JPopupMenu getComponentPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem(NbBundle.getMessage(UIBundle.class, "LBL_Copy"));
        copyItem.setEnabled(getSelectedText() != null);
        copyItem.addActionListener((ActionEvent e) -> {
            String text = getSelectedText();
            if (text != null && !text.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(text), null);
            }
        });
        menu.add(copyItem);
        return menu;
    }
}
