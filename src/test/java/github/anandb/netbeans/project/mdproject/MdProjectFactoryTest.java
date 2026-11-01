package github.anandb.netbeans.project.mdproject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import github.anandb.netbeans.support.PluginSettings;

/** The Markdown project type toggle must gate project recognition. */
class MdProjectFactoryTest {

    @Test
    void markerOnlyRecognisedWhileTheTypeIsEnabled(@TempDir Path dir) throws Exception {
        boolean original = PluginSettings.isMarkdownProjectEnabled();
        FileObject projectDir = FileUtil.toFileObject(dir.toFile());
        Files.writeString(dir.resolve(MdProjectFactory.MARKER), "");
        projectDir.refresh();

        MdProjectFactory factory = new MdProjectFactory();
        try {
            PluginSettings.setMarkdownProjectEnabled(true);
            assertTrue(factory.isProject(projectDir));

            PluginSettings.setMarkdownProjectEnabled(false);
            assertFalse(factory.isProject(projectDir),
                    "the factory must decline every directory while the type is disabled");
        } finally {
            PluginSettings.setMarkdownProjectEnabled(original);
        }
    }

    @Test
    void directoryWithoutTheMarkerIsNotAProject(@TempDir Path dir) {
        boolean original = PluginSettings.isMarkdownProjectEnabled();
        FileObject projectDir = FileUtil.toFileObject(dir.toFile());
        try {
            PluginSettings.setMarkdownProjectEnabled(true);
            assertFalse(new MdProjectFactory().isProject(projectDir));
        } finally {
            PluginSettings.setMarkdownProjectEnabled(original);
        }
    }
}
