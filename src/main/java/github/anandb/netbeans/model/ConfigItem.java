package github.anandb.netbeans.model;

public class ConfigItem {
    public final String name;
    public final String value;
    public final String baseName;
    public boolean isInternalUpdate = false;

    public ConfigItem(String name, String value) {
        this(name, value, name);
    }

    public ConfigItem(String name, String value, String baseName) {
        this.name = name;
        this.value = value;
        this.baseName = baseName;
    }

    @Override
    public String toString() {
        return name;
    }
}
