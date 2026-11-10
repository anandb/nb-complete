package github.anandb.netbeans.project.mdproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFilter;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class MdLogicalViewProviderTest {

    @Test
    void logicalViewShowsVisibleFilesAndFindsTheProjectRoot(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("README.md"), "visible");
        Files.writeString(dir.resolve("ignored.md"), "ignored");
        Files.writeString(dir.resolve(".DS_Store"), "system file");
        FileObject folder = FileUtil.toFileObject(dir.toFile());
        MdProject project = new MdProject(folder);
        MdProjectIgnoredFiles.setIgnoredPatterns(project, java.util.List.of("ignored.md"));
        folder.refresh();

        MdLogicalViewProvider provider = project.getLookup().lookup(MdLogicalViewProvider.class);
        DataFolder dataFolder = mock(DataFolder.class);
        when(dataFolder.createNodeChildren(any(DataFilter.class))).thenReturn(Children.LEAF);
        Node root;
        DataFilter filter;
        try (MockedStatic<DataFolder> dataFolders = mockStatic(DataFolder.class)) {
            dataFolders.when(() -> DataFolder.findFolder(folder)).thenReturn(dataFolder);
            root = provider.createLogicalView();
            ArgumentCaptor<DataFilter> filterCaptor = ArgumentCaptor.forClass(DataFilter.class);
            verify(dataFolder).createNodeChildren(filterCaptor.capture());
            filter = filterCaptor.getValue();
        }

        assertSame(folder, root.getLookup().lookup(FileObject.class));
        assertEquals(folder.getNameExt(), root.getDisplayName());
        assertTrue(filter.acceptDataObject(dataObject(folder.getFileObject("README.md"))));
        assertFalse(filter.acceptDataObject(dataObject(folder.getFileObject("ignored.md"))));
        assertFalse(filter.acceptDataObject(dataObject(folder.getFileObject(".DS_Store"))));
        assertSame(root, provider.findPath(root, folder));
        assertNull(provider.findPath(root, folder.getFileObject("README.md")));
    }

    private static DataObject dataObject(FileObject file) {
        DataObject dataObject = mock(DataObject.class);
        when(dataObject.getPrimaryFile()).thenReturn(file);
        return dataObject;
    }
}
