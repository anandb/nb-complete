package github.anandb.netbeans.project.mdproject;

import org.netbeans.spi.project.ui.CustomizerProvider2;
import org.netbeans.spi.project.ui.support.ProjectCustomizer;
import org.openide.util.HelpCtx;

/**
 * {@link CustomizerProvider2} that opens a project properties dialog
 * for {@link MdProject}. Panels are contributed via the
 * {@code Projects/github-anandb-netbeans-mdproject/Customizer} folder
 * in the system filesystem.
 */
public final class MdProjectCustomizerProvider implements CustomizerProvider2 {

    private static final String CUSTOMIZER_FOLDER =
        "Projects/github-anandb-netbeans-mdproject/Customizer";

    private final MdProject project;

    MdProjectCustomizerProvider(MdProject project) {
        this.project = project;
    }

    @Override
    public void showCustomizer() {
        showCustomizer(null, null);
    }

    @Override
    public void showCustomizer(String preselectedCategory, String preselectedSubCategory) {
        ProjectCustomizer.createCustomizerDialog(
            CUSTOMIZER_FOLDER,
            project.getLookup(),
            preselectedCategory,
            evt -> { /* ignore result */ },
            HelpCtx.DEFAULT_HELP
        ).setVisible(true);
    }
}
