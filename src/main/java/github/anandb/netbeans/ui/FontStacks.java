package github.anandb.netbeans.ui;

public final class FontStacks {

    private FontStacks() {}

    public static final String FONT_STACK = String.join(", ",
            "'Dialog'", "'Noto Sans'", "'Segoe UI'", "'Ubuntu'", "'Helvetica Neue'",
            "'Arial'", "'Apple Color Emoji'", "'Segoe UI Emoji'", "'Segoe UI Symbol'",
            "'Noto Color Emoji'", "'sans-serif'");

    public static final String MONO_STACK = String.join(", ",
            "'MesloLGS NF'", "'Source Code Pro'", "'JetBrains Mono'",
            "'Monaco'", "'Fira Code'", "'monospace'");
}
