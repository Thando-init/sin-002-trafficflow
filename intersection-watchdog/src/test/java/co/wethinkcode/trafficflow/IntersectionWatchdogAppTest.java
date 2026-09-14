package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectionWatchdogAppTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;

    @AfterEach
    void stopApplication() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void healthyStateReturnsOk() throws Exception {
        WatchdogState state = new WatchdogState(Duration.ofSeconds(10), Instant.now());
        startWith(state);

        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("{\"status\":\"OK\"}", response.body());
    }

    @Test
    void missedHeartbeatReturnsDetailedServiceUnavailableAlert() throws Exception {
        Instant startedAt = Instant.now().minusSeconds(11);
        WatchdogState state = new WatchdogState(Duration.ofSeconds(10), startedAt);
        state.evaluate(Instant.now());
        startWith(state);

        HttpResponse<String> health = get("/health");
        HttpResponse<String> alert = get("/alert");

        assertEquals(503, health.statusCode());
        assertTrue(health.body().contains("MISSED_HEARTBEAT"));
        assertTrue(health.body().contains("lastHeartbeatAt"));
        assertEquals(200, alert.statusCode());
        assertTrue(alert.body().contains("\"status\":\"ALERT\""));
        assertTrue(alert.body().contains("detectedAt"));
    }

    private void startWith(WatchdogState state) {
        app = IntersectionWatchdogApp.createApp(state).start(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
