package co.wethinkcode.trafficflow;

import java.util.Optional;

/** Abstractions for routing's external intersection and congestion dependencies. */
public final class RouteDependencies {
    private RouteDependencies() {
    }

    public interface IntersectionClient {
        Optional<IntersectionDetails> findById(String id);
    }

    public interface CongestionProvider {
        int currentLevel();
    }
}
