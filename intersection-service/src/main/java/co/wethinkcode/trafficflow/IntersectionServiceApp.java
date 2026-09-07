package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** HTTP entry point for the canonical intersection catalogue. */
public class IntersectionServiceApp {

    static final int PORT = 7021;
    static final URI DEFAULT_INGESTION_ENDPOINT = URI.create("http://localhost:7020/intersections");

    private IntersectionServiceApp() {
    }

    /** Starts the service, initializes its dependencies, and binds the HTTP API to the configured port.
     */
    public static void main(String[] args) {
//        Javalin app = Javalin.create().start(7021);
//
//        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Validates intersection/district names (source of truth). => Done in IntersectionCatalog.java)
        // Add domain endpoints for intersection-service here.
        IntersectionCatalog catalog = new IntersectionCatalog(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                ingestionEndpoint()
        );
        try {
            catalog.refresh();
            System.out.printf("Loaded %d intersections from ingestion-service.%n", catalog.size());
        } catch (IOException exception) {
            System.err.printf("Intersection catalogue is empty because ingestion-service could not be reached: %s%n",
                    exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Initial catalogue refresh was interrupted.");
        }

        HeartbeatPublisher heartbeatPublisher = new HeartbeatPublisher();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> publishHeartbeat(heartbeatPublisher), 0,
                heartbeatIntervalSeconds(), TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            heartbeatPublisher.close();
        }, "intersection-heartbeat-shutdown"));

        createApp(catalog).start(PORT);

    }

    /** Documents the createApp operation and its effect on service state or external communication.
     */
    static Javalin createApp(IntersectionCatalog catalog) {
        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson()));
        app.get("/health", context -> context.json(Map.of("status", "OK", "catalogueSize", catalog.size())));
        app.get("/intersections", context -> context.json(catalog.all()));
        app.get("/intersections/{id}", context -> catalog.findById(context.pathParam("id"))
                .ifPresentOrElse(context::json, () -> context.status(404)
                        .json(Map.of("error", "Unknown intersection"))));
        app.get("/districts/{district}", context -> {
            String district = context.pathParam("district");
            if (!catalog.districtExists(district)) {
                context.status(404).json(Map.of("error", "Unknown district"));
                return;
            }
            context.json(catalog.findByDistrict(district));
        });
        app.post("/intersections/refresh", context -> {
            try {
                int count = catalog.refresh();
                context.json(Map.of("loaded", count));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                context.status(503).json(Map.of("error", "Catalogue refresh interrupted"));
            } catch (IOException exception) {
                context.status(503).json(Map.of("error", "Ingestion service unavailable", "detail", exception.getMessage()));
            }
        });
        return app;
    }

    /** Documents the publishHeartbeat operation and its effect on service state or external communication.
     */
    private static void publishHeartbeat(HeartbeatPublisher heartbeatPublisher) {
        try {
            heartbeatPublisher.publish();
        } catch (IllegalStateException exception) {
            System.err.printf("Heartbeat publication failed: %s%n", exception.getMessage());
        }
    }

    /** Reads and validates the optional heartbeat interval environment setting.
     */
    private static long heartbeatIntervalSeconds() {
        String configured = System.getenv("HEARTBEAT_INTERVAL_SECONDS");
        if (configured == null || configured.isBlank()) {
            return 5;
        }
        try {
            return Math.max(1, Long.parseLong(configured));
        } catch (NumberFormatException exception) {
            System.err.println("Invalid HEARTBEAT_INTERVAL_SECONDS; using 5 seconds.");
            return 5;
        }
    }

    /** Reads the ingestion endpoint from the environment or uses the local default.
     */
    private static URI ingestionEndpoint() {
        String configured = System.getenv("INGESTION_SERVICE_URL");
        return configured == null || configured.isBlank() ? DEFAULT_INGESTION_ENDPOINT : URI.create(configured);
    }
}

// MQ TODO: publishes a periodic heartbeat to ActiveMQ queue MqConfig.HEARTBEAT_QUEUE at
// MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig), consumed by intersection-watchdog.
