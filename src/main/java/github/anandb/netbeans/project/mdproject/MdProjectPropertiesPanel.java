package github.anandb.netbeans.project.mdproject;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.event.ActionEvent;
import org.netbeans.spi.project.ui.support.ProjectCustomizer;
import org.netbeans.spi.project.ui.support.ProjectCustomizer.Category;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Project properties panel for configuring ignored folders/patterns
 * in a {@link MdProject}. Patterns are persisted to the
 * {@code .mdproject-ignore} file in the project root.
 */
@ProjectCustomizer.CompositeCategoryProvider.Registration(
    projectType = "github-anandb-netbeans-mdproject",
    position = 100
)
public final class MdProjectPropertiesPanel extends JPanel
        implements ProjectCustomizer.CompositeCategoryProvider {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<String> listModel;
    private final JList<String> patternList;
    private MdProject project;

    private MdProjectPropertiesPanel() {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel header = new JLabel(NbBundle.getMessage(
                MdProjectPropertiesPanel.class, "LBL_IgnoredFiles"));
        add(header, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        patternList = new JList<>(listModel);
        patternList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(patternList);
        scroll.setPreferredSize(new Dimension(400, 200));
        add(scroll, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton addBtn = new JButton(NbBundle.getMessage(
                MdProjectPropertiesPanel.class, "LBL_AddPattern"));
        addBtn.addActionListener(e -> onAddPattern());
        JButton removeBtn = new JButton(NbBundle.getMessage(
                MdProjectPropertiesPanel.class, "LBL_RemovePattern"));
        removeBtn.addActionListener(e -> onRemovePattern());
        btnPanel.add(addBtn);
        btnPanel.add(removeBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }

    // ---- CompositeCategoryProvider ----

    @Override
    public Category createCategory(Lookup context) {
        MdProject p = context.lookup(MdProject.class);
        if (p == null) {
            return null; // hide panel if no mdproject in context
        }
        return ProjectCustomizer.Category.create(
                "IgnoredFiles",
                NbBundle.getMessage(MdProjectPropertiesPanel.class, "LBL_CategoryIgnoredFiles"),
                null);
    }

    @Override
    public JComponent createComponent(Category category, Lookup context) {
        MdProject p = context.lookup(MdProject.class);
        if (p == null) {
            return new JPanel(); // fallback
        }
        this.project = p;

        // Load current patterns
        List<String> patterns = MdProjectIgnoredFiles.getIgnoredPatterns(p);
        listModel.clear();
        for (String pat : patterns) {
            listModel.addElement(pat);
        }

        // Save on OK/store
        category.setStoreListener((ActionEvent e) -> savePatterns());

        return this;
    }

    // ---- actions ----

    private void onAddPattern() {
        String input = JOptionPane.showInputDialog(this,
                NbBundle.getMessage(MdProjectPropertiesPanel.class, "MSG_EnterPattern"),
                NbBundle.getMessage(MdProjectPropertiesPanel.class, "LBL_AddPatternTitle"),
                JOptionPane.PLAIN_MESSAGE);
        if (input != null && !input.trim().isEmpty()) {
            listModel.addElement(input.trim());
        }
    }

    private void onRemovePattern() {
        int idx = patternList.getSelectedIndex();
        if (idx >= 0) {
            listModel.remove(idx);
        }
    }

    private void savePatterns() {
        if (project == null) {
            return;
        }
        List<String> patterns = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            patterns.add(listModel.get(i));
        }
        try {
            MdProjectIgnoredFiles.setIgnoredPatterns(project, patterns);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(MdProjectPropertiesPanel.class,
                            "MSG_SaveError", ex.getMessage()),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
