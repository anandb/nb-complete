package github.anandb.netbeans.ui.mdproject;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeListener;

import javax.swing.SwingUtilities;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.OnStart;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.platform.PlatformBridge;

/**
 * Keeps the <em>Markdown Project</em> entry in the New Project wizard in step with
 * <em>Enable Markdown Project Type</em>.
 *
 * <p>The wizard entry is created in the writable user layer instead of {@code layer.xml}, because a
 * module-layer file cannot be removed at runtime — a disabled project type would still be listed in
 * the wizard. Creating it here means switching the option off deletes the entry and switching it
 * back on restores it, without an IDE restart.</p>
 */
@OnStart
public final class MarkdownProjectTemplate implements Runnable {

    private static final Logger LOG = Logger.from(MarkdownProjectTemplate.class);

    /** Category folder shown in the New Project wizard. */
    static final String CATEGORY = "Templates/Project/Coding Assistant";
    /** Layers the wizard iterates; the name is arbitrary but stable. */
    static final String ENTRY = "github-anandb-netbeans-ui-mdproject-MdProjectWizardIterator.instance";

    private static final String ICON = "github/anandb/netbeans/ui/icons/mdproject.svg";
    private static final String DESCRIPTION_URL =
            "nbresloc:/github/anandb/netbeans/ui/mdproject/MdProjectDescription.html";

    /**
     * Serialises the layer edits, and keeps them ordered: {@link #apply()}'s EDT branch must not
     * use the unlimited-parallelism default processor, or a quick disable/enable double-toggle
     * could land remove after create and leave the entry deleted while the preference says on.
     */
    private static final RequestProcessor LAYER_EDITS =
            new RequestProcessor("Markdown project wizard entry", 1);

    /** The lock a create and a remove can never slip through at the same time. */
    private static final Object LOCK = new Object();

    private final PreferenceChangeListener listener = evt -> {
        if (PreferenceKeys.ACTIONS_MARKDOWN_PROJECT.equals(evt.getKey())) {
            apply();
        }
    };

    @Override
    public void run() {
        apply();
        PlatformBridge.prefStoreSafe().addChangeListener(listener);
    }

    /** Adds or removes the wizard entry for the current setting. Safe to call repeatedly. */
    static void apply() {
        if (SwingUtilities.isEventDispatchThread()) {
            // Layer edits are file I/O. Preference events arrive off the EDT today, but the guard
            // keeps any future EDT caller (e.g. a direct call from a dialog) from writing there.
            LAYER_EDITS.post(MarkdownProjectTemplate::applyNow);
            return;
        }
        applyNow();
    }

    private static void applyNow() {
        synchronized (LOCK) {
            if (PluginSettings.isMarkdownProjectEnabled()) {
                create();
            } else {
                remove();
            }
        }
        // The project manager caches "this directory is not a project" answers, and the factory
        // refuses every directory while the type is off: without this an existing folder that was
        // probed in the disabled state stays invisible after the option is switched back on.
        cacheClear().run();
    }

    /** The clear call, reroutable so tests can observe it (see {@link #setCacheClearForTest}). */
    private static Runnable cacheClear() {
        Runnable override = cacheClearForTest;
        return override != null ? override : MdProjectCache::clear;
    }

    /** Test seam only: a bare unit-test classpath has no project manager to observe. */
    private static volatile Runnable cacheClearForTest;

    static void setCacheClearForTest(Runnable override) {
        cacheClearForTest = override;
    }

    private static void create() {
        try {
            FileObject folder = FileUtil.getConfigFile(CATEGORY);
            if (folder == null) {
                folder = FileUtil.createFolder(FileUtil.getConfigRoot(), CATEGORY);
            }
            if (folder.getAttribute("displayName") == null) {
                // Re-applied, not only on creation: a crash right after createFolder would
                // otherwise leave the category unnamed for ever.
                folder.setAttribute("displayName", NbBundle.getMessage(UIBundle.class, "LBL_ProjectCategory"));
            }
            if (folder.getAttribute("position") == null) {
                folder.setAttribute("position", 9900);
            }
            FileObject entry = folder.getFileObject(ENTRY);
            if (entry != null && entry.getAttribute("instantiatingIterator") == null) {
                // A leftover from an interrupted run: the wizard needs the iterator attribute, and
                // an entry without one would open a broken panel.
                entry.delete();
                entry = null;
            }
            if (entry == null) {
                entry = folder.createData(ENTRY);
                entry.setAttribute("instantiatingIterator", new MdProjectWizardIterator());
                entry.setAttribute("iconBase", ICON);
                entry.setAttribute("templateWizardURL", new URL(DESCRIPTION_URL));
                entry.setAttribute("template", Boolean.TRUE);
            }
            // The user layer stores the resolved string, so a language change needs a refresh.
            String name = NbBundle.getMessage(MdProjectWizardIterator.class, "LBL_WizardName");
            if (!name.equals(entry.getAttribute("displayName"))) {
                entry.setAttribute("displayName", name);
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Could not register the Markdown project template", ex);
        }
    }

    private static void remove() {
        FileObject folder = FileUtil.getConfigFile(CATEGORY);
        if (folder == null) {
            return;
        }
        FileObject entry = folder.getFileObject(ENTRY);
        if (entry == null) {
            return;
        }
        try {
            // Only our entry: the category folder may carry other templates one day.
            entry.delete();
            // ...but a category with nothing left in it must not linger in the wizard.
            if (folder.getChildren().length == 0) {
                folder.delete();
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Could not remove the Markdown project template", ex);
        }
    }
}
