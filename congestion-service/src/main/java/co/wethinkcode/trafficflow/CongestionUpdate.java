package co.wethinkcode.trafficflow;

/** Request payload for PUT /congestion/{intersectionId} to update the congestion level of an intersection. */
public record CongestionUpdate(Integer level) {
    /** Validates the congestion level, throwing an exception if it is invalid. */
    public CongestionUpdate {
        if (level == null) {
            throw new IllegalArgumentException("Congestion level must not be null");
        }
        CongestionLevel.validate(level);
    }

}
