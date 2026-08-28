package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionServiceAppTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;

    @AfterEach
    void stopApplication() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void healthEndpointReturnsOk() throws Exception {
        startWith(List.of());

        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    void intersectionsEndpointReturnsCleanedRecordsAsJson() throws Exception {
        startWith(List.of(new IntersectionCleaner.Intersection("INT-1", "Downtown", "4-way", true)));

        HttpResponse<String> response = get("/intersections");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
        assertTrue(response.body().contains("\"id\":\"INT-1\""));
        assertTrue(response.body().contains("\"district\":\"Downtown\""));
    }

    @Test
    void intersectionsEndpointReturnsAnEmptyJsonArrayForEmptyInput() throws Exception {
        startWith(List.of());

        HttpResponse<String> response = get("/intersections");

        assertEquals(200, response.statusCode());
        assertEquals("[]", response.body());
    }

    private void startWith(List<IntersectionCleaner.Intersection> intersections) {
        app = IngestionServiceApp.createApp(intersections).start(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
