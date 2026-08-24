package github.anandb.netbeans.tasks;

import java.util.Set;

/**
 * Tag editor: a {@link ChipEditor} specialized for {@code @}-prefixed tags.
 * Retains the legacy {@code getTags}/{@code setTags}/{@code getSelectedTags}/
 * {@code setSelectedTags}/{@code setAvailableTags} API used by the task and
 * query editors while delegating all rendering to {@link ChipEditor}.
 */
final class TagsEditor extends ChipEditor {

    TagsEditor() {
        super("@", "Tag", Set.of());
    }

    /** Current selection as a comma-separated string (legacy API). */
    String getTags() {
        return getChips();
    }

    /** Current selection as a set of bare values (legacy API). */
    Set<String> getSelectedTags() {
        return getSelected();
    }

    /** Replace the selection from a comma-separated string (legacy API). */
    void setTags(String tags) {
        setChips(tags);
    }

    /** Replace the selection from a set of bare values (legacy API). */
    void setSelectedTags(Set<String> tags) {
        setSelected(tags);
    }

    /** Populate the suggestions dropdown (legacy API). */
    void setAvailableTags(Set<String> tags) {
        setAvailable(tags);
    }
}
