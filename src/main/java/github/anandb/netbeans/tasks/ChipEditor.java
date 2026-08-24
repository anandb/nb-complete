package github.anandb.netbeans.tasks;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
 * Chip-based multi-select editor. Selected values are shown as removable pills;
 * a combobox of predefined suggestions adds a value on selection, and a
 * free-text field adds custom values on Enter. State is kept as a
 * case-insensitive, order-preserving set and can be accessed as either a
 * {@link Set} or a comma-separated {@link String}.
 *
 * <p>The {@code prefix} (e.g. {@code @} for tags, {@code +} for projects) is
 * displayed on each chip and prepended when values are rendered as text; the
 * underlying value set stores the bare token (without the prefix).</p>
 */
class ChipEditor extends JPanel {

    private final String prefix;
    private static final Color CHIP_BG = new Color(52, 120, 246, 40);
    private static final Color CHIP_BORDER = new Color(52, 120, 246);

    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private final JPanel chipField = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private final JTextField field = new JTextField(14);
    private final JComboBox<String> suggestions = new JComboBox<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private final String label;
    private String placeholder = "";
    private boolean rebuilding;

    ChipEditor(String prefix, String label, Set<String> available) {
        super(new BorderLayout(4, 0));
        this.prefix = prefix == null ? "" : prefix;
        this.label = label == null ? "" : label;
        this.placeholder = "Enter " + this.label;

        chipField.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        add(chipField, BorderLayout.CENTER);

        suggestions.setPrototypeDisplayValue("Select " + this.label);
        suggestions.setPreferredSize(new Dimension(200, suggestions.getPreferredSize().height));
        add(suggestions, BorderLayout.EAST);

        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setForeground(Color.GRAY);
                    field.setText(placeholder);
                }
            }
        });
        field.addActionListener(e -> {
            addValue(field.getText());
            field.setText("");
            field.setForeground(Color.BLACK);
        });
        suggestions.addActionListener(e -> {
            if (rebuilding) {
                return;
            }
            Object sel = suggestions.getSelectedItem();
            String prompt = "Select " + label;
            if (sel != null && !sel.toString().equals(prompt)) {
                addValue(sel.toString());
            }
            if (suggestions.getItemCount() > 0) {
                suggestions.setSelectedIndex(0); // keep the prompt shown
            }
        });
        setAvailable(available);
        // Initialize the placeholder text (grayed) until the user types.
        field.setForeground(Color.GRAY);
        field.setText(placeholder);
        refresh();
    }

    /** Sets the placeholder shown in the text box / combo prompt. */
    void setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Populate the suggestions dropdown (e.g. from repository vocabulary), sorted alphabetically. */
    void setAvailable(Set<String> values) {
        rebuilding = true;
        try {
            suggestions.removeAllItems();
            String prompt = "Select " + label;
            suggestions.addItem(prompt);
            if (values != null) {
                values.stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(suggestions::addItem);
            }
        } finally {
            rebuilding = false;
        }
    }

    /** Current selection as an immutable copy of bare values (no prefix). */
    Set<String> getSelected() {
        return new LinkedHashSet<>(selected);
    }

    /** Replace the current selection from a set of bare values. */
    void setSelected(Set<String> values) {
        selected.clear();
        if (values != null) {
            selected.addAll(values);
        }
        refresh();
    }

    /** Replace the current selection from a comma-separated string. */
    void setChips(String values) {
        selected.clear();
        if (values != null) {
            for (String t : values.split(",")) {
                String v = t.trim();
                if (!v.isEmpty()) {
                    selected.add(v);
                }
            }
        }
        refresh();
    }

    /** Current selection as a comma-separated string of bare values. */
    String getChips() {
        return String.join(", ", selected);
    }

    /** Register a listener notified whenever the selection changes. */
    void addChangeListener(Runnable r) {
        changeListeners.add(r);
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private void addValue(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return;
        }
        // Accept values typed with or without the prefix.
        if (!prefix.isEmpty() && v.startsWith(prefix)) {
            v = v.substring(prefix.length()).trim();
        }
        if (v.isEmpty()) {
            return;
        }
        final String cand = v;
        boolean exists = selected.stream().anyMatch(x -> x.equalsIgnoreCase(cand));
        if (!exists) {
            selected.add(v);
            refresh();
            notifyChanged();
        }
    }

    private void removeValue(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return;
        }
        if (!prefix.isEmpty() && v.startsWith(prefix)) {
            v = v.substring(prefix.length()).trim();
        }
        final String cand = v;
        String match = selected.stream()
                .filter(x -> x.equalsIgnoreCase(cand)).findFirst().orElse(null);
        if (match != null) {
            selected.remove(match);
            refresh();
            notifyChanged();
        }
    }

    private void refresh() {
        chipField.removeAll();
        for (String v : selected) {
            chipField.add(buildChip(v));
        }
        chipField.add(field);
        chipField.revalidate();
        chipField.repaint();
    }

    private JPanel buildChip(String value) {
        String label = prefix.isEmpty() ? value : prefix + value;
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CHIP_BORDER, 1, true),
                BorderFactory.createEmptyBorder(1, 5, 1, 3)));
        chip.setBackground(CHIP_BG);
        chip.add(new JLabel(label));
        JButton remove = new JButton("x");
        remove.setBorderPainted(false);
        remove.setContentAreaFilled(false);
        remove.setFocusPainted(false);
        remove.setMargin(new Insets(0, 2, 0, 2));
        remove.setToolTipText("Remove " + label);
        remove.addActionListener(ev -> removeValue(value));
        chip.add(remove);
        return chip;
    }

    private void notifyChanged() {
        for (Runnable r : changeListeners) {
            r.run();
        }
    }
}
