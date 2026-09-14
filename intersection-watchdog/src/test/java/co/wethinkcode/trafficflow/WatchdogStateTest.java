package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchdogStateTest {
    private static final Instant STARTED_AT = Instant.parse("2026-08-27T09:00:00Z");

    @Test
    void remainsHealthyWithinInitialHeartbeatGracePeriod() {
        WatchdogState state = new WatchdogState(Duration.ofSeconds(10), STARTED_AT);

        state.evaluate(STARTED_AT.plusSeconds(10));

        assertTrue(state.status().healthy());
        assertNull(state.status().alert());
    }

    @Test
    void raisesMissedHeartbeatAlertAfterGracePeriod() {
        WatchdogState state = new WatchdogState(Duration.ofSeconds(10), STARTED_AT);

        state.evaluate(STARTED_AT.plusSeconds(11));

        assertFalse(state.status().healthy());
        assertEquals("MISSED_HEARTBEAT", state.status().alert().type());
    }

    @Test
    void heartbeatClearsAlertAndResetsTimeoutWindow() {
        WatchdogState state = new WatchdogState(Duration.ofSeconds(10), STARTED_AT);
        state.evaluate(STARTED_AT.plusSeconds(11));

        state.recordHeartbeat(STARTED_AT.plusSeconds(12));
        state.evaluate(STARTED_AT.plusSeconds(21));

        assertTrue(state.status().healthy());
        assertEquals(STARTED_AT.plusSeconds(12), state.status().lastHeartbeatAt());
    }

    @Test
    void deadLetterRaisesObservableAlertImmediately() {
        WatchdogState state = new WatchdogState(Duration.ofSeconds(10), STARTED_AT);

        state.recordDeadLetter("A heartbeat was dead-lettered", STARTED_AT.plusSeconds(1));

        assertFalse(state.status().healthy());
        assertEquals("DEAD_LETTER", state.status().alert().type());
    }

    @Test
    void rejectsZeroOrNegativeTimeouts() {
        assertThrows(IllegalArgumentException.class, () -> new WatchdogState(Duration.ZERO, STARTED_AT));
        assertThrows(IllegalArgumentException.class, () -> new WatchdogState(Duration.ofSeconds(-1), STARTED_AT));
    }
}
