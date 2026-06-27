package github.anandb.netbeans.ui.mdproject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

/**
 * Wizard iterator for creating a new Markdown Project.
 * Registered in layer.xml under {@code Templates/Project/}.
 */
public final class MdProjectWizardIterator implements WizardDescriptor.InstantiatingIterator<WizardDescriptor> {

    private static final String GITIGNORE_CONTENT = ""
            + "# OS files\n"
            + ".DS_Store\n"
            + "Thumbs.db\n"
            + "Desktop.ini\n"
            + "\n"
            + "# Editor swap/backup\n"
            + "*.swp\n"
            + "*.swo\n"
            + "*~\n"
            + "\n"
            + "# Project marker (internal)\n"
            + ".mdproject\n"
            + "\n"
            + "# Build output\n"
            + "dist/\n"
            + "build/\n"
            + "\n"
            + "# IDE project files\n"
            + "nbproject/private/\n";

    private static final String MDPROJECT_CONTENT = ""
            + "# Markdown Project\n"
            + "# This marker file identifies the directory as a Markdown Project in NetBeans.\n"
            + "\n"
            + "name={0}\n"
            + "created={1}\n";

    private WizardDescriptor wizard;
    private int index;
    private List<WizardDescriptor.Panel<WizardDescriptor>> panels;

    @Override
    public Set<?> instantiate() throws IOException {
        String name = (String) wizard.getProperty("projName");
        String folder = (String) wizard.getProperty("projDir");
        File projectDir = new File(folder, name);

        if (!projectDir.mkdirs()) {
            throw new IOException("Could not create directory: " + projectDir);
        }
        FileObject dir = FileUtil.toFileObject(projectDir);
        if (dir == null) {
            throw new IOException("FileUtil.toFileObject returned null for: " + projectDir);
        }

        // Create .mdproject marker file with project metadata
        FileObject mdprojectFile = dir.createData(".mdproject");
        try (OutputStream out = mdprojectFile.getOutputStream()) {
            String content = java.text.MessageFormat.format(MDPROJECT_CONTENT,
                    name, java.time.LocalDate.now().toString());
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }

        // Create .gitignore with sensible defaults
        FileObject gitignore = dir.getFileObject(".gitignore");
        if (gitignore == null) {
            gitignore = dir.createData(".gitignore");
        }
        try (OutputStream out = gitignore.getOutputStream()) {
            out.write(GITIGNORE_CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        // Return dir — NetBeans wizard framework automatically opens and
        // registers the project. clearNonProjectCache is NOT called here
        // because it can trigger EDT-intensive rescans that cause a
        // spinning cursor while the wizard is still on screen.

        return Collections.singleton(dir);
    }

    @Override
    public void initialize(WizardDescriptor wiz) {
        this.wizard = wiz;
        index = 0;
        panels = List.of(new MdProjectWizardPanel());
    }

    @Override
    public void uninitialize(WizardDescriptor wiz) {
        wizard = null;
        panels = null;
    }

    @Override
    public WizardDescriptor.Panel<WizardDescriptor> current() {
        return panels.get(index);
    }

    @Override
    public String name() {
        return NbBundle.getMessage(MdProjectWizardIterator.class, "LBL_WizardName");
    }

    @Override
    public boolean hasNext() {
        return index < panels.size() - 1;
    }

    @Override
    public boolean hasPrevious() {
        return index > 0;
    }

    @Override
    public void nextPanel() {
        if (hasNext()) {
            index++;
        }
    }

    @Override
    public void previousPanel() {
        if (hasPrevious()) {
            index--;
        }
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        // no-op — wizard handles validation
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        // no-op
    }
}
