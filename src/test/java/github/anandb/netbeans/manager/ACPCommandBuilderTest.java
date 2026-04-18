package github.anandb.netbeans.manager;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.prefs.Preferences;

import org.apache.commons.exec.CommandLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ACPCommandBuilderTest {

    @Test
    void testBuildDefaultCommand() throws Exception {
        String prefs = buildPrefs("", "acp --log-level DEBUG");
        Preferences.importPreferences(new ByteArrayInputStream(prefs.getBytes(StandardCharsets.UTF_8)));

        String defaultPath = System.getProperty("user.home") + "/.opencode/bin/opencode";
        CommandLine cmd = new ACPCommandBuilder(Preferences.userRoot()).build(defaultPath);
        assertNotNull(cmd);
        assertEquals(defaultPath, cmd.getExecutable());

        String[] args = cmd.getArguments();
        assertNotNull(args);
        assertEquals(3, args.length);
        assertEquals("acp", args[0]);
        assertEquals("--log-level", args[1]);
        assertEquals("DEBUG", args[2]);
    }

    @Test
    void testBuildWithCustomArguments() throws Exception {
        String prefs = buildPrefs("/custom/path/opencode", "chat --model claude");
        Preferences.importPreferences(new ByteArrayInputStream(prefs.getBytes(StandardCharsets.UTF_8)));
        CommandLine cmd = new ACPCommandBuilder(Preferences.userRoot()).build("");
        assertEquals("/custom/path/opencode", cmd.getExecutable());

        String[] args = cmd.getArguments();
        assertEquals(3, args.length);
        assertEquals("chat", args[0]);
        assertEquals("--model", args[1]);
        assertEquals("claude", args[2]);
    }

    @Test
    void testBuildWithEmptyArguments() throws Exception {
        String prefs = buildPrefs("/path/opencode", "");
        Preferences.importPreferences(new ByteArrayInputStream(prefs.getBytes(StandardCharsets.UTF_8)));
        CommandLine cmd = new ACPCommandBuilder(Preferences.userRoot()).build("/path/opencode");
        assertEquals("/path/opencode", cmd.getExecutable());

        String[] args = cmd.getArguments();
        assertEquals(1, args.length);
        assertEquals("acp", args[0]);
    }

    @Test
    void testBuildExecutableNotNullOrEmpty() throws Exception {
        String prefs = buildPrefs("/path/somepath", "");
        Preferences.importPreferences(new ByteArrayInputStream(prefs.getBytes(StandardCharsets.UTF_8)));
        CommandLine cmd = new ACPCommandBuilder(Preferences.userRoot()).build("/path/opencode");
        assertNotNull(cmd.getExecutable());
        assertEquals("/path/somepath", cmd.getExecutable());
        assertFalse(cmd.getExecutable().isEmpty());
    }

    private String buildPrefs(String exec, String args) {
        return String.format(Locale.ROOT,
            """
            <!DOCTYPE preferences SYSTEM "http://java.sun.com/dtd/preferences.dtd">
            <preferences>
             <root type="user">
                <map>
                   <entry key="acpExecutablePath" value="%s"/>
                   <entry key="processArguments" value="%s"/>
                </map>
            </root>
            </preferences>
            """,
            exec, args
        );
    }
}