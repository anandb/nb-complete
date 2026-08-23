package github.anandb.netbeans.tasks;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Chip-based multi-select tag editor. Selected tags are shown as removable
 * pills; a combobox of predefined suggestions adds a tag on selection, and a
 * free-text field adds custom tags on Enter. State is kept as a
 * case-insensitive, order-preserving set and can be accessed as either a
 * {@link Set} or a comma-separated {@link String}.
 */
final class TagsEditor extends JPanel {

    private static final Color TAG_BG = new Color(52, 120, 246, 40);
    private static final Color TAG_BORDER = new Color(52, 120, 246);

    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private final JPanel tagField = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private final JTextField field = new JTextField(14);
    private final JComboBox<String> suggestions = new JComboBox<>();
    private final List<Runnable> changeListeners = new ArrayList<>();

    TagsEditor() {
        super(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 0, 2, 0);
        g.gridy = 0;
        g.gridx = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;
        tagField.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        add(tagField, g);

        g.gridx = 1;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        suggestions.setPrototypeDisplayValue("help wanted");
        suggestions.setPreferredSize(new java.awt.Dimension(160, suggestions.getPreferredSize().height));
        add(suggestions, g);

        field.addActionListener(e -> {
            addTag(field.getText());
            field.setText("");
        });
        suggestions.addActionListener(e -> {
            Object sel = suggestions.getSelectedItem();
            if (sel != null) {
                addTag(sel.toString());
            }
            suggestions.setSelectedIndex(-1);
        });
        refresh();
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Populate the suggestions dropdown (e.g. from repository vocabulary). */
    void setAvailableTags(Set<String> tags) {
        suggestions.removeAllItems();
        for (String t : tags) {
            suggestions.addItem(t);
        }
    }

    /** Current selection as an immutable copy. */
    Set<String> getSelectedTags() {
        return new LinkedHashSet<>(selected);
    }

    /** Replace the current selection from a set. */
    void setSelectedTags(Set<String> tags) {
        selected.clear();
        if (tags != null) {
            selected.addAll(tags);
        }
        refresh();
    }

    /** Replace the current selection from a comma-separated string. */
    void setTags(String tags) {
        selected.clear();
        if (tags != null) {
            for (String t : tags.split(",")) {
                String v = t.trim();
                if (!v.isEmpty()) {
                    selected.add(v);
                }
            }
        }
        refresh();
    }

    /** Current selection as a comma-separated string. */
    String getTags() {
        return String.join(", ", selected);
    }

    /** Register a listener notified whenever the selection changes. */
    void addChangeListener(Runnable r) {
        changeListeners.add(r);
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private void addTag(String raw) {
        String tag = raw == null ? "" : raw.trim();
        if (tag.isEmpty()) {
            return;
        }
        boolean exists = selected.stream().anyMatch(v -> v.equalsIgnoreCase(tag));
        if (!exists) {
            selected.add(tag);
            refresh();
            notifyChanged();
        }
    }

    private void removeTag(String raw) {
        String tag = raw == null ? "" : raw.trim();
        if (tag.isEmpty()) {
            return;
        }
        String match = selected.stream()
                .filter(v -> v.equalsIgnoreCase(tag)).findFirst().orElse(null);
        if (match != null) {
            selected.remove(match);
            refresh();
            notifyChanged();
        }
    }

    private void refresh() {
        tagField.removeAll();
        for (String tag : selected) {
            tagField.add(buildChip(tag));
        }
        tagField.add(field);
        tagField.revalidate();
        tagField.repaint();
    }

    private JPanel buildChip(String tag) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TAG_BORDER, 1, true),
                BorderFactory.createEmptyBorder(1, 5, 1, 3)));
        chip.setBackground(TAG_BG);
        chip.add(new JLabel(tag));
        JButton remove = new JButton("x");
        remove.setBorderPainted(false);
        remove.setContentAreaFilled(false);
        remove.setFocusPainted(false);
        remove.setMargin(new Insets(0, 2, 0, 2));
        remove.setToolTipText("Remove " + tag);
        remove.addActionListener(ev -> removeTag(tag));
        chip.add(remove);
        return chip;
    }

    private void notifyChanged() {
        for (Runnable r : changeListeners) {
            r.run();
        }
    }
}
