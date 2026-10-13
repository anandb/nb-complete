package github.anandb.netbeans.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum MessageType {
    agent_message_chunk,
    agent_thought_chunk,
    available_commands_update,
    plan,
    tool_call,
    tool_call_update,
    usage_update,
    user_message_chunk,
    // Wire name is the ACP-standard singular form; the plural/older spellings are
    // accepted as aliases so older harnesses keep decoding.
    @JsonAlias("config_options_update")
    config_option_update,
    @JsonAlias("session_info_updated")
    session_info_update,
    current_mode_update,
    message,
    error_response,
    info,
    responding_finished,
    end_turn;

    public boolean isThought() {
        return name().contains("thought");
    }

    public boolean isTool() {
        return name().contains("tool");
    }

    public boolean isUser() {
        return name().contains("user");
    }

    public boolean isAssistant() {
        return name().startsWith("agent") && !name().contains("thought");
    }

     private boolean isError() {
        return name().contains("error");
    }

    private boolean isInfo() {
        return name().equals("info");
    }

    public String roleName() {
        if (isThought()) return "thought";
        if (isTool()) return "tool";
        if (isUser()) return "user";
        if (isAssistant()) return "assistant";
        if (isError()) return "error";
        if (isInfo()) return "info";
        return null;
    }

}
