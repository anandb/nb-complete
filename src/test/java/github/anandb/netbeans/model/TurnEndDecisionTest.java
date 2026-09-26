package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnEndDecisionTest {

    @Test
    void respondingFinishedEndsTurnForEveryAgent() {
        assertTrue(TurnEndDecision.of("responding_finished", false).endOfTurn());
        assertTrue(TurnEndDecision.of("responding_finished", true).endOfTurn());
    }

    @Test
    void endTurnEndsTurnForEveryAgent() {
        assertTrue(TurnEndDecision.of("end_turn", false).endOfTurn());
        assertTrue(TurnEndDecision.of("end_turn", true).endOfTurn());
    }

    @Test
    void availableCommandsUpdateIsNeverTurnEnd() {
        // The goose wedge: available_commands_update arrives at turn START; treating it
        // as turn-end lets the next prompt bypass the queue guard and drops the
        // in-flight prompt. Holds for queueing AND interleaved agents.
        assertFalse(TurnEndDecision.of("available_commands_update", true).endOfTurn());
        assertFalse(TurnEndDecision.of("available_commands_update", false).endOfTurn());
    }

    @Test
    void availableCommandsUpdateReadiesPreambleOnlyForInterleavedAgents() {
        assertTrue(TurnEndDecision.of("available_commands_update", false).preambleReady());
        assertFalse(TurnEndDecision.of("available_commands_update", true).preambleReady());
    }

    @Test
    void turnEndSignalsNeverReadyPreambleEarly() {
        // endOfTurn already clears the preamble wait via its own branch; preambleReady
        // stays false so the two outcomes are distinguishable.
        assertFalse(TurnEndDecision.of("responding_finished", false).preambleReady());
        assertFalse(TurnEndDecision.of("end_turn", false).preambleReady());
    }

    @Test
    void unrelatedOrMissingTypeDecidesNothing() {
        TurnEndDecision chunk = TurnEndDecision.of("agent_message_chunk", false);
        assertFalse(chunk.endOfTurn());
        assertFalse(chunk.preambleReady());
        TurnEndDecision empty = TurnEndDecision.of("", false);
        assertFalse(empty.endOfTurn());
        assertFalse(empty.preambleReady());

        TurnEndDecision missing = TurnEndDecision.of((String) null, false);
        assertFalse(missing.endOfTurn());
        assertFalse(missing.preambleReady());
        TurnEndDecision missingTyped = TurnEndDecision.of((MessageType) null, false);
        assertFalse(missingTyped.endOfTurn());
        assertFalse(missingTyped.preambleReady());
    }
}
