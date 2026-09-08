package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.util.Map;
import java.util.function.IntConsumer;

/**
 * HTTP API for the current city-wide congestion level (0-8).
 * The service publishes the congestion level to an ActiveMQ topic for other services to consume.
 */
public class CongestionServiceApp {

    static final int PORT = 7022;

    private CongestionServiceApp() {
    }

    /** Starts the service, initializes its dependencies, and binds the HTTP API to the configured port.
     */
    public static void main(String[] args) {
        CongestionPublisher publisher = new CongestionPublisher();
        createApp(new CongestionLevel(), publisher::publish).start(PORT);
    }


    /**
     * Creates the HTTP application for reading and updating the city-wide
     * congestion level.
     *
     * @param congestionLevel the in-memory congestion state
     * @param eventPublisher callback used to publish changed levels to ActiveMQ
     * @return a configured Javalin application
     */
    static Javalin createApp(CongestionLevel congestionLevel, IntConsumer eventPublisher) {
        Javalin app = Javalin.create(config ->
                config.jsonMapper(new JavalinJackson())
        );

        app.get("/health", context ->
                context.json(Map.of("status", "OK"))
        );

        app.get("/congestion", context ->
                context.json(Map.of("level", congestionLevel.current()))
        );

        app.put("/congestion", context -> {
            final CongestionUpdate update;

            // Parsing and validation happen while Jackson constructs CongestionUpdate.
            // Any malformed JSON or out-of-range level is a client error.
            try {
                update = context.bodyAsClass(CongestionUpdate.class);
            } catch (Exception exception) {
                context.status(400).json(Map.of(
                        "error", "Congestion level must be an integer between 0 and 8 inclusive"
                ));
                return;
            }

            final boolean changed;
            try {
                changed = congestionLevel.update(update.level());
            } catch (IllegalArgumentException exception) {
                context.status(400).json(Map.of("error", exception.getMessage()));
                return;
            }

            // Do not publish idempotent updates. A broker failure is a service error,
            // not a malformed request, so it is returned as HTTP 503.
            if (changed) {
                try {
                    eventPublisher.accept(update.level());
                } catch (RuntimeException exception) {
                    context.status(503).json(Map.of(
                            "error", "Unable to publish congestion update"
                    ));
                    return;
                }
            }

            context.json(Map.of(
                    "level", congestionLevel.current(),
                    "changed", changed
            ));
        });

        return app;
    }

}

        // TODO (Tracks the city-wide Congestion Level (0-8).)
        // Add domain endpoints for congestion-service here.




