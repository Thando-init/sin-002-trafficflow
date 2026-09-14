package co.wethinkcode.trafficflow;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Holds the most recent valid congestion event received from the broker. */
public final class LatestCongestionLevel implements RouteDependencies.CongestionProvider {
    private final AtomicInteger level;
    private final AtomicReference<Instant> updatedAt;

    /** Documents the LatestCongestionLevel operation and its effect on service state or external communication.
     */
    public LatestCongestionLevel(int initialLevel) {
        validate(initialLevel);
        this.level = new AtomicInteger(initialLevel);
        this.updatedAt = new AtomicReference<>(Instant.EPOCH);
    }

    @Override
    /** Calls the congestion service and returns its validated numeric level.
     */
    public int currentLevel() {
        return level.get();
    }

    /** Validates and stores a congestion level, reporting whether it changed.
     */
    public void update(int newLevel) {
        validate(newLevel);
        level.set(newLevel);
        updatedAt.set(Instant.now());
    }

    /** Returns the timestamp of the latest accepted broker update.
     */
    public Instant updatedAt() {
        return updatedAt.get();
    }

    /** Rejects congestion values outside the inclusive range zero through eight.
     */
    private static void validate(int level) {
        if (level < 0 || level > 8) {
            throw new IllegalArgumentException("Congestion level must be between 0 and 8 inclusive");
        }
    }
}
