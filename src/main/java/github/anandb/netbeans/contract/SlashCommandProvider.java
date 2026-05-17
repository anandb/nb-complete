package github.anandb.netbeans.contract;

/**
 * Extension point for registering slash commands via {@code @ServiceProvider}.
 * Implementations are discovered by {@link github.anandb.netbeans.manager.SlashCommandInterceptor}
 * at construction time via {@code Lookup.getDefault().lookupAll(SlashCommandProvider.class)}.
 */
public interface SlashCommandProvider {

    /**
     * The command string including leading slash, e.g. {@code "/new"}.
     *
     * @return the command string
     */
    String getCommand();

    /**
     * Human-readable description shown in autocomplete.
     *
     * @return the description string
     */
    String getDescription();

    /**
     * Handler to invoke when the command is triggered.
     *
     * @return the slash command handler
     */
    SlashCommandHandler getHandler();
}
