package co.wethinkcode.trafficflow;

import java.util.concurrent.atomic.AtomicInteger;

/** Thread-safe city-wide congestion level, constrained to the inclusive range 0 through 8. */
public final class CongestionLevel {
    public static final int MINIMUM_LEVEL = 0;
    public static final int MAXIMUM_LEVEL = 8;

    private final AtomicInteger level;

    /** Documents the CongestionLevel operation and its effect on service state or external communication.
     */
    public CongestionLevel() {
        this(MINIMUM_LEVEL);
    }

    /** Documents the CongestionLevel operation and its effect on service state or external communication.
     */
    public CongestionLevel(int initialLevel) {
        validate(initialLevel);
        this.level = new AtomicInteger(initialLevel);
    }

    /** Returns the current city-wide congestion level.
     */
    public int current() {
        return level.get();
    }

    /**
     * Updates the level.
     *
     * @return {@code true} only when the value changed
     */
    /** Validates and stores a congestion level, reporting whether it changed.
     */
    public boolean update(int newLevel) {
        validate(newLevel);
        return level.getAndSet(newLevel) != newLevel;
    }

    /** Rejects congestion values outside the inclusive range zero through eight.
     */
    public static void validate(int level) {
        if (level < MINIMUM_LEVEL || level > MAXIMUM_LEVEL) {
            throw new IllegalArgumentException("Congestion level must be between 0 and 8 inclusive");
        }
    }
}
