package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpIntersectionClientTest {
    private Javalin mockServer;

    @AfterEach
    void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @Test
    void returnsDecodedIntersectionForSuccessfulResponse() {
        startMockServer();
        HttpIntersectionClient client = new HttpIntersectionClient(HttpClient.newHttpClient(), new ObjectMapper(), baseUri());

        Optional<IntersectionDetails> result = client.findById("INT-1");

        assertTrue(result.isPresent());
        assertEquals("Downtown", result.orElseThrow().district());
    }

    @Test
    void mapsNotFoundToEmptyOptional() {
        startMockServer();
        HttpIntersectionClient client = new HttpIntersectionClient(HttpClient.newHttpClient(), new ObjectMapper(), baseUri());

        assertTrue(client.findById("missing").isEmpty());
    }

    @Test
    void mapsServerErrorToDependencyFailure() {
        startMockServer();
        HttpIntersectionClient client = new HttpIntersectionClient(HttpClient.newHttpClient(), new ObjectMapper(), baseUri());

        DependencyUnavailableException exception = assertThrows(DependencyUnavailableException.class,
                () -> client.findById("error"));

        assertTrue(exception.getMessage().contains("HTTP 500"));
    }

    @Test
    void mapsMalformedPayloadToDependencyFailure() {
        startMockServer();
        HttpIntersectionClient client = new HttpIntersectionClient(HttpClient.newHttpClient(), new ObjectMapper(), baseUri());

        assertThrows(DependencyUnavailableException.class, () -> client.findById("malformed"));
    }

    @Test
    void mapsUnreachableServiceToDependencyFailure() {
        HttpIntersectionClient client = new HttpIntersectionClient(HttpClient.newHttpClient(), new ObjectMapper(),
                URI.create("http://localhost:1"));

        assertThrows(DependencyUnavailableException.class, () -> client.findById("INT-1"));
    }

    private void startMockServer() {
        mockServer = Javalin.create(config -> config.jsonMapper(new io.javalin.json.JavalinJackson()))
                .get("/intersections/{id}", context -> {
                    switch (context.pathParam("id")) {
                        case "INT-1" -> context.result("{\"id\":\"INT-1\",\"district\":\"Downtown\",\"signalType\":\"4-way\",\"active\":true}");
                        case "missing" -> context.status(404);
                        case "error" -> context.status(500);
                        default -> context.result("not-json");
                    }
                })
                .start(0);
    }

    private URI baseUri() {
        return URI.create("http://localhost:" + mockServer.port());
    }
}
