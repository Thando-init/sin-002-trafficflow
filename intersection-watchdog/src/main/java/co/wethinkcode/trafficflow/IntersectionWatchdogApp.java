package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** HTTP entry point for intersection-service liveness monitoring. */
public final class IntersectionWatchdogApp {
    static final int PORT = 7024;

    private IntersectionWatchdogApp() {
    }


    /** Starts the service, initialises its dependencies, and binds the HTTP API to the configured port.
     */
    public static void main(String[] args) {
        WatchdogState state = new WatchdogState(Duration.ofSeconds(heartbeatTimeoutSeconds()), Instant.now());
        HeartbeatConsumer consumer = new HeartbeatConsumer(state);
        try {
            consumer.start();
        } catch (IllegalStateException exception) {
            System.err.printf("Watchdog cannot connect to the heartbeat queue: %s%n", exception.getMessage());
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> state.evaluate(Instant.now()), 1, 1, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            consumer.close();
        }, "watchdog-shutdown"));

        createApp(state).start(PORT);

        // TODO (Cries for help if the Intersection Service crashes, since routes can no longer be validated.)
        // Mechanism: ActiveMQ Queue heartbeat/dead-letter
    }

    /** Documents the createApp operation and its effect on service state or external communication.
     */
    static Javalin createApp(WatchdogState state) {
        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson()));
        app.get("/health", context -> {
            WatchdogState.WatchdogStatus status = state.status();
            if (status.healthy()) {
                context.json(Map.of("status", "OK"));
            } else {
                context.status(503).json(response(status));
            }
        });
        app.get("/alert", context -> context.json(response(state.status())));
        return app;
    }

    /** Converts watchdog state into a JSON-safe response with string timestamps.
     */
    private static Map<String, Object> response(WatchdogState.WatchdogStatus status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status.healthy() ? "OK" : "ALERT");
        response.put("healthy", status.healthy());
        response.put("lastHeartbeatAt", status.lastHeartbeatAt() == null ? null : status.lastHeartbeatAt().toString());
        if (status.alert() == null) {
            response.put("alert", null);
        } else {
            response.put("alert", Map.of(
                    "type", status.alert().type(),
                    "detail", status.alert().detail(),
                    "detectedAt", status.alert().detectedAt().toString()
            ));
        }
        return response;
    }

    /** Reads and validates the optional watchdog timeout environment setting.
     */
    private static long heartbeatTimeoutSeconds() {
        String configured = System.getenv("HEARTBEAT_TIMEOUT_SECONDS");
        if (configured == null || configured.isBlank()) {
            return 15;
        }
        try {
            return Math.max(1, Long.parseLong(configured));
        } catch (NumberFormatException exception) {
            System.err.println("Invalid HEARTBEAT_TIMEOUT_SECONDS; using 15 seconds.");
            return 15;
        }
    }

}

// MQ TODO: subscribes to ActiveMQ queue MqConfig.HEARTBEAT_QUEUE at MqConfig.BROKER_URL
// (see co.wethinkcode.trafficflow.mq.MqConfig) and alerts if a heartbeat from
// intersection-service is missed or a message lands in the dead-letter queue.
