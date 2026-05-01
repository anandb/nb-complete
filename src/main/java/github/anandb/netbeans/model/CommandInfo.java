package github.anandb.netbeans.model;

import github.anandb.netbeans.contract.SlashCommandHandler;

public record CommandInfo(SlashCommandHandler handler, String description) {}
