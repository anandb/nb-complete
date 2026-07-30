package github.anandb.netbeans.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Finite-state machine states for a chat session's lifecycle.
 *
 * <h3>Valid Transitions</h3>
 * <pre>
 *            ┌─────────┐
 *   ┌────────│  IDLE   │◄────────┐
 *   │        └────┬────┘         │
 *   │             │              │
 *   │        ┌────▼────┐         │
 *   ├────────│ LOADING │         │
 *   │        └────┬────┘         │
 *   │             │              │
 *   │        ┌────▼──────┐       │
 *   ├────────│ STREAMING │       │
 *   │        └────┬──────┘       │
 *   │             │              │
 *   │        ┌────▼──────┐       │
 *   └────────│ STOPPING  │───────┘
 *            └───────────┘
 * </pre>
 *
 * <ul>
 *   <li>{@link #IDLE} &rarr; {@link #LOADING} (start new or open session)</li>
 *   <li>{@link #LOADING} &rarr; {@link #STREAMING} (session ready)</li>
 *   <li>{@link #LOADING} &rarr; {@link #IDLE} (load failure)</li>
 *   <li>{@link #STREAMING} &rarr; {@link #LOADING} (reload)</li>
 *   <li>{@link #STREAMING} &rarr; {@link #STOPPING} (user stop or turn end)</li>
 *   <li>{@link #STREAMING} &rarr; {@link #IDLE} (server disconnect)</li>
 *   <li>{@link #STOPPING} &rarr; {@link #STREAMING} (recovery timeout)</li>
 *   <li>{@link #STOPPING} &rarr; {@link #IDLE} (server confirmed stop)</li>
 * </ul>
 *
 * The allowed transitions are enforced by {@link #canTransitionTo(SessionState)}
 * via the precomputed sets {@code FROM_IDLE}, {@code FROM_LOADING},
 * {@code FROM_STREAMING}, and {@code FROM_STOPPING}.
 */
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
