package github.anandb.netbeans.manager;

import java.util.prefs.Preferences;

import org.apache.commons.exec.CommandLine;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

public class ACPCommandBuilder {

    private final Preferences prefs;

    public ACPCommandBuilder(Preferences prefs) {
        this.prefs = prefs;
    }

    public CommandLine build(String defaultOpenCodePath) {
        String exec = getAcpExecutablePath(defaultOpenCodePath);
        String args = getProcessArguments("acp");

        CommandLine cmd = new CommandLine(exec);
        cmd.addArguments(args, true);
        return cmd;
    }

    private String getAcpExecutablePath(String defaultValue) {
        return defaultIfBlank(prefs.get("acpExecutablePath", defaultValue), defaultValue);
    }

    private String getProcessArguments(String defaultValue) {
        return defaultIfBlank(prefs.get("processArguments", defaultValue), defaultValue);
    }
}