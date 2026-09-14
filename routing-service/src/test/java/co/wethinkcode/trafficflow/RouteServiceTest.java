package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteServiceTest {
    private static final IntersectionDetails ORIGIN = new IntersectionDetails("INT-1", "Downtown", "4-way", true);
    private static final IntersectionDetails DESTINATION = new IntersectionDetails("INT-2", "Midtown", "roundabout", true);

    @Test
    void estimateIncreasesWithCongestionAndIncludesIntersectionEffects() {
        int uncongested = RouteEstimator.estimateMinutes(10, 0, ORIGIN, DESTINATION);
        int congested = RouteEstimator.estimateMinutes(10, 8, ORIGIN, DESTINATION);

        assertEquals(13, uncongested);
        assertEquals(25, congested);
        assertTrue(congested > uncongested);
    }

    @Test
    void rejectsBaseMinutesOutsideAllowedRange() {
        assertThrows(IllegalArgumentException.class, () -> RouteEstimator.estimateMinutes(0, 1, ORIGIN, DESTINATION));
        assertThrows(IllegalArgumentException.class, () -> RouteEstimator.estimateMinutes(181, 1, ORIGIN, DESTINATION));
    }

    @Test
    void validRouteUsesValidatedEndpointsAndCurrentCongestion() {
        RouteService service = routeService(Map.of("origin", ORIGIN, "destination", DESTINATION), () -> 4);

        RouteResult result = service.estimate(new RouteRequest("origin", "destination", 20));

        assertTrue(result.isReady());
        assertEquals(4, result.estimate().congestionLevel());
        assertEquals(35, result.estimate().estimatedMinutes());
    }

    @Test
    void unknownEndpointReturnsInvalidRouteRatherThanAnEstimate() {
        RouteService service = routeService(Map.of("origin", ORIGIN), () -> 2);

        RouteResult result = service.estimate(new RouteRequest("origin", "missing", 12));

        assertEquals(RouteResult.Status.INVALID_ROUTE, result.status());
        assertEquals(null, result.estimate());
    }

    @Test
    void inactiveEndpointIsRejected() {
        IntersectionDetails inactive = new IntersectionDetails("INT-2", "Midtown", "roundabout", false);
        RouteService service = routeService(Map.of("origin", ORIGIN, "destination", inactive), () -> 2);

        RouteResult result = service.estimate(new RouteRequest("origin", "destination", 12));

        assertEquals(RouteResult.Status.INVALID_ROUTE, result.status());
    }

    @Test
    void unavailableDependencyReturnsDedicatedFailure() {
        RouteDependencies.IntersectionClient unavailable = id -> {
            throw new DependencyUnavailableException("Intersection service is unavailable");
        };
        RouteService service = new RouteService(unavailable, () -> 2);

        RouteResult result = service.estimate(new RouteRequest("origin", "destination", 12));

        assertEquals(RouteResult.Status.DEPENDENCY_UNAVAILABLE, result.status());
    }

    @Test
    void invalidRequestIsReportedBeforeDependencyCalls() {
        RouteService service = routeService(Map.of("origin", ORIGIN, "destination", DESTINATION), () -> 2);

        RouteResult result = service.estimate(new RouteRequest("origin", "destination", 0));

        assertEquals(RouteResult.Status.INVALID_REQUEST, result.status());
    }

    @Test
    void latestCongestionCacheAcceptsOnlyValidMessageLevels() {
        LatestCongestionLevel level = new LatestCongestionLevel(0);
        Instant initialTimestamp = level.updatedAt();

        level.update(6);

        assertEquals(6, level.currentLevel());
        assertNotEquals(initialTimestamp, level.updatedAt());
        assertThrows(IllegalArgumentException.class, () -> level.update(9));
        assertEquals(6, level.currentLevel());
    }

    private static RouteService routeService(Map<String, IntersectionDetails> intersections,
                                             RouteDependencies.CongestionProvider congestionProvider) {
        return new RouteService(id -> Optional.ofNullable(intersections.get(id)), congestionProvider);
    }
}
