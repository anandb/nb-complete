package github.anandb.netbeans.model;

import java.util.EnumSet;
import java.util.Set;

public enum SessionState {
    IDLE,
    LOADING,
    STREAMING,
    STOPPING;

    private static final Set<SessionState> FROM_IDLE = EnumSet.of(LOADING);
    private static final Set<SessionState> FROM_LOADING = EnumSet.of(STREAMING, IDLE);
    private static final Set<SessionState> FROM_STREAMING = EnumSet.of(LOADING, STOPPING, IDLE);
    private static final Set<SessionState> FROM_STOPPING = EnumSet.of(STREAMING, IDLE);

    public boolean canTransitionTo(SessionState target) {
        return switch (this) {
            case IDLE -> FROM_IDLE.contains(target);
            case LOADING -> FROM_LOADING.contains(target);
            case STREAMING -> FROM_STREAMING.contains(target);
            case STOPPING -> FROM_STOPPING.contains(target);
        };
    }

    public boolean canSendMessage() {
        return this == STREAMING;
    }

    public boolean canLoadSession() {
        return this == IDLE || this == STREAMING;
    }

    public boolean canStopMessage() {
        return this == STREAMING;
    }

    public boolean isActive() {
        return this == LOADING || this == STREAMING || this == STOPPING;
    }
}
