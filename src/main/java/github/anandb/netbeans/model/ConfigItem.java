package github.anandb.netbeans.model;

public record ConfigItem(String name, String value, String baseName) {
    public ConfigItem(String name, String value) {
        this(name, value, name);
    }

    @Override
    public String toString() {
        return name;
    }
}
