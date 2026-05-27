package github.anandb.netbeans.contract;

/** SPI for registering slash commands via @ServiceProvider. */
public interface SlashCommandProvider {

    /** @return command string including leading slash, e.g. "/new". */
    String getCommand();

    /** @return human-readable description shown in autocomplete. */
    String getDescription();

    /** @return handler to invoke when command is triggered. */
    SlashCommandHandler getHandler();
}
