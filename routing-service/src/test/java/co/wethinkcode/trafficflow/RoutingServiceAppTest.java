package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingServiceAppTest {
    private static final IntersectionDetails ORIGIN = new IntersectionDetails("INT-1", "Downtown", "4-way", true);
    private static final IntersectionDetails DESTINATION = new IntersectionDetails("INT-2", "Midtown", "roundabout", true);

    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;

    @AfterEach
    void stopApplication() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void estimateEndpointReturnsSuccessfulEstimate() throws Exception {
        startWith(routeService(Map.of("INT-1", ORIGIN, "INT-2", DESTINATION), () -> 3));

        HttpResponse<String> response = post("{\"originId\":\"INT-1\",\"destinationId\":\"INT-2\",\"baseMinutes\":10}");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"estimatedMinutes\":18"));
    }

    @Test
    void invalidRouteMapsToNotFound() throws Exception {
        startWith(routeService(Map.of("INT-1", ORIGIN), () -> 0));

        HttpResponse<String> response = post("{\"originId\":\"INT-1\",\"destinationId\":\"NOPE\",\"baseMinutes\":10}");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("known intersections"));
    }

    @Test
    void invalidRequestMapsToBadRequest() throws Exception {
        startWith(routeService(Map.of("INT-1", ORIGIN, "INT-2", DESTINATION), () -> 0));

        HttpResponse<String> response = post("{\"originId\":\"INT-1\",\"destinationId\":\"INT-2\",\"baseMinutes\":0}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("between 1 and 180"));
    }

    @Test
    void dependencyFailureMapsToServiceUnavailable() throws Exception {
        RouteService service = new RouteService(id -> {
            throw new DependencyUnavailableException("Intersection service unavailable");
        }, () -> 0);
        startWith(service);

        HttpResponse<String> response = post("{\"originId\":\"INT-1\",\"destinationId\":\"INT-2\",\"baseMinutes\":10}");

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("unavailable"));
    }

    @Test
    void malformedJsonMapsToBadRequest() throws Exception {
        startWith(routeService(Map.of("INT-1", ORIGIN, "INT-2", DESTINATION), () -> 0));

        HttpResponse<String> response = post("not-json");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("valid JSON"));
    }

    private void startWith(RouteService routeService) {
        app = RoutingServiceApp.createApp(routeService).start(0);
    }

    private HttpResponse<String> post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + "/routes/estimate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static RouteService routeService(Map<String, IntersectionDetails> intersections,
                                             RouteDependencies.CongestionProvider provider) {
        return new RouteService(id -> Optional.ofNullable(intersections.get(id)), provider);
    }
}
