package co.wethinkcode.trafficflow;

/** Signals that a required downstream service could not provide a valid response. */
public final class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Documents the DependencyUnavailableException operation and its effect on service state or external communication.
     */
    public DependencyUnavailableException(String message) {
        super(message);
    }
}
