package github.anandb.netbeans.ui;

/**
 * Bundle anchor for the whole UI layer. {@code NbBundle.getMessage} resolves
 * {@code Bundle.properties} from the package of the class it is given, so every UI class —
 * including the ones in subpackages — passes this class instead of its own. Do NOT move it out
 * of {@code github.anandb.netbeans.ui}: the lookup of every caller would break.
 */
public final class UIBundle {

    private UIBundle() {
    }
}
