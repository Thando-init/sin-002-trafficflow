package co.wethinkcode.trafficflow;

/** Request payload for a route estimate between two intersections. */
public record RouteRequest(String originId, String destinationId, Integer baseMinutes) {
}
