package github.anandb.netbeans.ui.mdproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import github.anandb.netbeans.support.PluginSettings;

/**
 * The New Project wizard must not list a project type the user switched off — and must get it back
 * when they switch it on again, without an IDE restart. Both transitions must also invalidate the
 * project manager's cached "not a project" answers.
 */
class MarkdownProjectTemplateTest {

    private boolean original;

    @BeforeEach
    void rememberSetting() {
        original = PluginSettings.isMarkdownProjectEnabled();
    }

    @AfterEach
    void restoreSetting() {
        MarkdownProjectTemplate.setCacheClearForTest(null);
        PluginSettings.setMarkdownProjectEnabled(original);
        MarkdownProjectTemplate.apply();
    }

    @Test
    void entryIsRemovedWhileTheTypeIsDisabled() {
        PluginSettings.setMarkdownProjectEnabled(false);
        MarkdownProjectTemplate.apply();

        assertNull(entry(), "a disabled project type must not offer a wizard entry");
        FileObject category = FileUtil.getConfigFile(MarkdownProjectTemplate.CATEGORY);
        assertTrue(category == null || category.getChildren().length == 0,
                "an empty category must not linger in the wizard");
    }

    @Test
    void entryIsRestoredWithTheAttributesTheWizardNeeds() {
        PluginSettings.setMarkdownProjectEnabled(false);
        MarkdownProjectTemplate.apply();
        PluginSettings.setMarkdownProjectEnabled(true);
        MarkdownProjectTemplate.apply();

        FileObject entry = entry();
        assertNotNull(entry, "the wizard entry must be back");
        assertTrue(entry.getAttribute("instantiatingIterator") instanceof MdProjectWizardIterator,
                "the wizard needs a live iterator instance");
        assertEquals(Boolean.TRUE, entry.getAttribute("template"));
        assertNotNull(entry.getAttribute("displayName"));
        assertNotNull(entry.getAttribute("templateWizardURL"));
    }

    @Test
    void anEntryLeftWithoutItsIteratorIsRepaired() throws Exception {
        PluginSettings.setMarkdownProjectEnabled(true);
        MarkdownProjectTemplate.apply();
        FileObject entry = entry();
        assertNotNull(entry);
        entry.setAttribute("instantiatingIterator", null);

        // Next start (or next apply) must not leave the wizard with a broken panel.
        MarkdownProjectTemplate.apply();

        FileObject repaired = entry();
        assertNotNull(repaired);
        assertTrue(repaired.getAttribute("instantiatingIterator") instanceof MdProjectWizardIterator);
    }

    @Test
    void theNonProjectCacheIsClearedOnEachTransition() {
        AtomicInteger clears = new AtomicInteger();
        MarkdownProjectTemplate.setCacheClearForTest(clears::incrementAndGet);

        PluginSettings.setMarkdownProjectEnabled(false);
        MarkdownProjectTemplate.apply();
        PluginSettings.setMarkdownProjectEnabled(true);
        MarkdownProjectTemplate.apply();

        assertEquals(2, clears.get(), "both toggle directions must invalidate the cache");
    }

    private static FileObject entry() {
        return FileUtil.getConfigFile(MarkdownProjectTemplate.CATEGORY + "/" + MarkdownProjectTemplate.ENTRY);
    }
}
