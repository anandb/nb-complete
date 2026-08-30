package github.anandb.netbeans.ui;

/**
 * Common contract for options panels hosted by {@link AbstractOptionsPanelController}.
 */
interface OptionsPanel {
    void load();
    void store();
    boolean valid();
}
