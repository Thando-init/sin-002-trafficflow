package co.wethinkcode.trafficflow;

/** The intersection fields routing needs after HTTP validation. */
public record IntersectionDetails(String id, String district, String signalType, Boolean active) {
    public boolean isRoutable() {
        return !Boolean.FALSE.equals(active);
    }
}
