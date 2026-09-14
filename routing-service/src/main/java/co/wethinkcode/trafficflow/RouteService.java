package co.wethinkcode.trafficflow;

import java.util.Objects;
import java.util.Optional;

/** Coordinates downstream validation and calculation for a route request. */
public final class RouteService {
    private final RouteDependencies.IntersectionClient intersectionClient;
    private final RouteDependencies.CongestionProvider congestionProvider;

    public RouteService(RouteDependencies.IntersectionClient intersectionClient,
                        RouteDependencies.CongestionProvider congestionProvider) {
        this.intersectionClient = Objects.requireNonNull(intersectionClient, "intersectionClient");
        this.congestionProvider = Objects.requireNonNull(congestionProvider, "congestionProvider");
    }

    /** Validates endpoint inputs, loads both intersections, obtains congestion, and calculates the route estimate.
     */
    public RouteResult estimate(RouteRequest request) {
        if (request == null || isBlank(request.originId()) || isBlank(request.destinationId()) || request.baseMinutes() == null) {
            return RouteResult.failure(RouteResult.Status.INVALID_REQUEST,
                    "originId, destinationId, and baseMinutes are required");
        }
        if (request.baseMinutes() < RouteEstimator.MINIMUM_BASE_MINUTES
                || request.baseMinutes() > RouteEstimator.MAXIMUM_BASE_MINUTES) {
            return RouteResult.failure(RouteResult.Status.INVALID_REQUEST,
                    "baseMinutes must be between 1 and 180 inclusive");
        }

        try {
            Optional<IntersectionDetails> origin = intersectionClient.findById(request.originId());
            Optional<IntersectionDetails> destination = intersectionClient.findById(request.destinationId());
            if (origin.isEmpty() || destination.isEmpty()) {
                return RouteResult.failure(RouteResult.Status.INVALID_ROUTE,
                        "Both originId and destinationId must identify known intersections");
            }
            if (!origin.get().isRoutable() || !destination.get().isRoutable()) {
                return RouteResult.failure(RouteResult.Status.INVALID_ROUTE,
                        "Routes cannot start or end at an inactive intersection");
            }

            int congestionLevel = congestionProvider.currentLevel();
            int estimatedMinutes = RouteEstimator.estimateMinutes(
                    request.baseMinutes(), congestionLevel, origin.get(), destination.get());
            return RouteResult.ready(new RouteResult.RouteEstimate(
                    origin.get().id(), destination.get().id(), request.baseMinutes(), congestionLevel, estimatedMinutes));
        } catch (DependencyUnavailableException exception) {
            return RouteResult.failure(RouteResult.Status.DEPENDENCY_UNAVAILABLE, exception.getMessage());
        }
    }

    /** Tests whether a required text value is absent or contains only whitespace.
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
