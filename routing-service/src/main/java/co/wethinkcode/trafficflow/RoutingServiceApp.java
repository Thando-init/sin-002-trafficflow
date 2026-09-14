package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;

/** HTTP entry point for message-driven route estimation.*/
public final class RoutingServiceApp {
    static final int PORT = 7023;
    static final URI DEFAULT_INTERSECTION_SERVICE_URL = URI.create("http://localhost:7021");

    /** Documents the RoutingServiceApp operation and its effect on service state or external communication.
     */
    private RoutingServiceApp() {
    }

    /** Starts the service, initialises its dependencies, and binds the HTTP API to the configured port.
     */
    public static void main(String[] args) {
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        LatestCongestionLevel latestCongestion = new LatestCongestionLevel(0);
        CongestionSubscriber subscriber = new CongestionSubscriber(latestCongestion, objectMapper);
        try {
            subscriber.start();
        } catch (IllegalStateException exception) {
            System.err.printf("Routing is starting with level 0 because the congestion topic is unavailable: %s%n",
                    exception.getMessage());
        }
        Runtime.getRuntime().addShutdownHook(new Thread(subscriber::close, "congestion-subscriber-shutdown"));

        RouteService routeService = new RouteService(
                new HttpIntersectionClient(httpClient, objectMapper,
                        serviceUri("INTERSECTION_SERVICE_URL", DEFAULT_INTERSECTION_SERVICE_URL)),
                latestCongestion
        );
        createApp(routeService).start(PORT);
         // TODO (Provides estimated travel times based on congestion and intersection.) => Done in RouteService.java
        // Add domain endpoints for routing-service here.
    }

    /** Documents the createApp operation and its effect on service state or external communication.
     */
    static Javalin createApp(RouteService routeService) {
        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson()));
        app.get("/health", context -> context.json(Map.of("status", "OK")));
        app.post("/routes/estimate", context -> {
            RouteResult result;
            try {
                result = routeService.estimate(context.bodyAsClass(RouteRequest.class));
            } catch (Exception exception) {
                context.status(400).json(Map.of("error", "Request body must be valid JSON"));
                return;
            }
            switch (result.status()) {
                case READY -> context.json(result.estimate());
                case INVALID_REQUEST -> context.status(400).json(Map.of("error", result.error()));
                case INVALID_ROUTE -> context.status(404).json(Map.of("error", result.error()));
                case DEPENDENCY_UNAVAILABLE -> context.status(503).json(Map.of("error", result.error()));
            }
        });
        return app;
    }

    /** Reads a service URL from the environment or returns its safe local default.
     */
    private static URI serviceUri(String environmentVariable, URI defaultUri) {
        String configured = System.getenv(environmentVariable);
        return configured == null || configured.isBlank() ? defaultUri : URI.create(configured);
    }

}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig)
