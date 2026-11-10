package github.anandb.netbeans.project.mdproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.project.ui.support.ProjectCustomizer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.HelpCtx;
import org.mockito.MockedStatic;

class MdProjectCustomizerTest {

    @Test
    void customizerProviderBuildsTheDialogAndShowsIt(@TempDir Path dir) {
        MdProject project = new MdProject(FileUtil.toFileObject(dir.toFile()));
        MdProjectCustomizerProvider provider =
                project.getLookup().lookup(MdProjectCustomizerProvider.class);
        Dialog dialog = mock(Dialog.class);

        try (MockedStatic<ProjectCustomizer> customizer = mockStatic(ProjectCustomizer.class)) {
            customizer.when(() -> ProjectCustomizer.createCustomizerDialog(
                    eq("Projects/github-anandb-netbeans-mdproject/Customizer"),
                    eq(project.getLookup()), eq("IgnoredFiles"), any(ActionListener.class),
                    eq(HelpCtx.DEFAULT_HELP))).thenReturn(dialog);

            provider.showCustomizer("IgnoredFiles", null);

            verify(dialog).setVisible(true);
            customizer.verify(() -> ProjectCustomizer.createCustomizerDialog(
                    eq("Projects/github-anandb-netbeans-mdproject/Customizer"),
                    eq(project.getLookup()), eq("IgnoredFiles"), any(ActionListener.class),
                    eq(HelpCtx.DEFAULT_HELP)));
        }
    }

    @Test
    void propertiesProviderRequiresAProjectAndBuildsItsPanel(@TempDir Path dir) {
        MdProject project = new MdProject(FileUtil.toFileObject(dir.toFile()));
        MdProjectPropertiesPanel.Provider provider = new MdProjectPropertiesPanel.Provider();

        assertNull(provider.createCategory(Lookup.EMPTY));
        assertInstanceOf(JPanel.class, provider.createComponent(null, Lookup.EMPTY));

        ProjectCustomizer.Category category = provider.createCategory(Lookups.fixed(project));

        assertNotNull(category);
        assertEquals("IgnoredFiles", category.getName());
        assertInstanceOf(MdProjectPropertiesPanel.class,
                provider.createComponent(category, Lookups.fixed(project)));
    }

    @Test
    void propertiesPanelLoadsAndSavesIgnorePatterns(@TempDir Path dir) throws Exception {
        FileObject folder = FileUtil.toFileObject(dir.toFile());
        MdProject project = new MdProject(folder);
        MdProjectIgnoredFiles.setIgnoredPatterns(project, List.of("build", "drafts"));
        ProjectCustomizer.Category category = mock(ProjectCustomizer.Category.class);

        new MdProjectPropertiesPanel(project, category);

        var captor = org.mockito.ArgumentCaptor.forClass(ActionListener.class);
        verify(category).setStoreListener(captor.capture());
        captor.getValue().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "store"));

        assertEquals("build\ndrafts", Files.readString(
                dir.resolve(".mdproject-ignore"), StandardCharsets.UTF_8)
                .lines().filter(line -> !line.startsWith("#") && !line.isBlank())
                .collect(java.util.stream.Collectors.joining("\n")));
    }
}
