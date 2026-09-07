package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class IntersectionServiceAppTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;

    @AfterEach
    void stopApplication() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void healthReportsCatalogueSize() throws Exception {
        startWithSampleCatalogue();

        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"catalogueSize\":2"));
    }

    @Test
    void lookupAcceptsCaseAndWhitespaceVariations() throws Exception {
        startWithSampleCatalogue();

        HttpResponse<String> response = get("/intersections/%20int-1%20");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\":\"INT-1\""));
    }

    @Test
    void districtEndpointReturnsMatchesAndRejectsUnknownDistrict() throws Exception {
        startWithSampleCatalogue();

        HttpResponse<String> known = get("/districts/downtown");
        HttpResponse<String> unknown = get("/districts/lakeside");

        assertEquals(200, known.statusCode());
        assertTrue(known.body().contains("\"id\":\"INT-1\""));
        assertEquals(404, unknown.statusCode());
    }

    @Test
    void unknownIntersectionReturnsNotFound() throws Exception {
        startWithSampleCatalogue();

        HttpResponse<String> response = get("/intersections/INT-404");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Unknown intersection"));
    }

    private void startWithSampleCatalogue() {
        IntersectionCatalog catalog = new IntersectionCatalog(HttpClient.newHttpClient(), new ObjectMapper(),
                URI.create("http://localhost:1/intersections"));
        catalog.replaceAll(List.of(
                new Intersection("INT-1", "Downtown", "4-way", true),
                new Intersection("INT-2", "Midtown", "roundabout", true)
        ));
        app = IntersectionServiceApp.createApp(catalog).start(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
