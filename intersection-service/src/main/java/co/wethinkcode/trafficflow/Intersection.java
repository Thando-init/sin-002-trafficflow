package co.wethinkcode.trafficflow;

/**
 * Canonical intersection representation obtained from the ingestion service.
 * Nullable metadata indicates an explicit gap in the source data.
 */
public record Intersection(String id, String district, String signalType, Boolean active) {
}
