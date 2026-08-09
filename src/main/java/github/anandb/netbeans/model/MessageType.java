package github.anandb.netbeans.model;

public enum MessageType {
    agent_message_chunk,
    agent_thought_chunk,
    available_commands_update,
    plan,
    tool_call,
    tool_call_update,
    usage_update,
    user_message_chunk,
    config_options_update,
    session_info_updated,
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
