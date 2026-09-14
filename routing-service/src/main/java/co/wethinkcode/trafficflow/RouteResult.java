package co.wethinkcode.trafficflow;

/** Result of route validation and travel-time calculation. */
public record RouteResult(Status status, String error, RouteEstimate estimate) {
    public enum Status {
        READY,
        INVALID_REQUEST,
        INVALID_ROUTE,
        DEPENDENCY_UNAVAILABLE
    }

    /** Creates a successful route result containing the computed estimate.
     */
    public static RouteResult ready(RouteEstimate estimate) {
        return new RouteResult(Status.READY, null, estimate);
    }

    /** Creates a route result representing a validation or dependency failure.
     */
    public static RouteResult failure(Status status, String error) {
        return new RouteResult(status, error, null);
    }

    /** Reports whether this result contains a usable route estimate.
     */
    public boolean isReady() {
        return status == Status.READY;
    }

    public record RouteEstimate(String originId, String destinationId, int baseMinutes,
                                int congestionLevel, int estimatedMinutes) {
    }
}
