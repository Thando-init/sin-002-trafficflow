package co.wethinkcode.trafficflow;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Thread-safe state for liveness monitoring of intersection-service. */
public final class WatchdogState {
    private final Duration heartbeatTimeout;
    private final Instant startedAt;
    private Instant lastHeartbeatAt;
    private Alert alert;

    /** Documents the WatchdogState operation and its effect on service state or external communication.
     */
    public WatchdogState(Duration heartbeatTimeout, Instant startedAt) {
        if (heartbeatTimeout.isNegative() || heartbeatTimeout.isZero()) {
            throw new IllegalArgumentException("heartbeatTimeout must be positive");
        }
        this.heartbeatTimeout = heartbeatTimeout;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }

    /** Records a heartbeat and clears any prior alert to represent recovery.
     */
    public synchronized void recordHeartbeat(Instant receivedAt) {
        lastHeartbeatAt = Objects.requireNonNull(receivedAt, "receivedAt");
        alert = null;
    }

    /** Raises an immediate alert for a dead-lettered heartbeat message.
     */
    public synchronized void recordDeadLetter(String detail, Instant detectedAt) {
        alert = new Alert("DEAD_LETTER", detail, Objects.requireNonNull(detectedAt, "detectedAt"));
    }

    /** Marks the service unavailable only after the initial grace period or a missed subsequent heartbeat. */
    public synchronized void evaluate(Instant now) {
        Objects.requireNonNull(now, "now");
        Instant expectedBy = (lastHeartbeatAt == null ? startedAt : lastHeartbeatAt).plus(heartbeatTimeout);
        if (now.isAfter(expectedBy)) {
            alert = new Alert("MISSED_HEARTBEAT", "No intersection-service heartbeat received within "
                    + heartbeatTimeout.toSeconds() + " seconds", now);
        }
    }

    /** Returns a consistent snapshot of watchdog health and alert state.
     */
    public synchronized WatchdogStatus status() {
        return new WatchdogStatus(alert == null, lastHeartbeatAt, alert);
    }

    /** Documents the WatchdogStatus operation and its effect on service state or external communication.
     */
    public record WatchdogStatus(boolean healthy, Instant lastHeartbeatAt, Alert alert) {
    }

    /** Documents the Alert operation and its effect on service state or external communication.
     */
    public record Alert(String type, String detail, Instant detectedAt) {
    }
}
