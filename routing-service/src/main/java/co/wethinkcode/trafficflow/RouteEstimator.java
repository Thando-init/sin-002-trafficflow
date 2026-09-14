package co.wethinkcode.trafficflow;

import java.util.Locale;

/** Pure calculation for estimated travel time after route endpoints have been validated. */
public final class RouteEstimator {
    public static final int MINIMUM_BASE_MINUTES = 1;
    public static final int MAXIMUM_BASE_MINUTES = 180;

    /** Documents the RouteEstimator operation and its effect on service state or external communication.
     */
    private RouteEstimator() {
    }

    /**
     * Applies a 15% delay for each congestion step and small intersection penalties.
     * The result always has at least one minute and is rounded up to avoid understating travel time.
     */
    public static int estimateMinutes(int baseMinutes, int congestionLevel,
                                      IntersectionDetails origin, IntersectionDetails destination) {
        if (baseMinutes < MINIMUM_BASE_MINUTES || baseMinutes > MAXIMUM_BASE_MINUTES) {
            throw new IllegalArgumentException("baseMinutes must be between 1 and 180 inclusive");
        }
        if (congestionLevel < 0 || congestionLevel > 8) {
            throw new IllegalArgumentException("Congestion level must be between 0 and 8 inclusive");
        }

        double congestionAdjusted = baseMinutes * (1.0 + congestionLevel * 0.15);
        return (int) Math.ceil(congestionAdjusted + intersectionPenalty(origin) + intersectionPenalty(destination));
    }

    /** Adds a small signal-related delay for the requested endpoint.
     */
    private static int intersectionPenalty(IntersectionDetails intersection) {
        if (intersection.signalType() == null) {
            return 2;
        }
        return switch (intersection.signalType().trim().toLowerCase(Locale.ROOT)) {
            case "roundabout" -> 1;
            case "4-way" -> 2;
            default -> 2;
        };
    }
}
