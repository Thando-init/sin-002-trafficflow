package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CongestionServiceAppTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;

    @AfterEach
    void stopApplication() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void getReturnsInitialCongestionLevel() throws Exception {
        startWith(new CongestionLevel(2), level -> { });

        HttpResponse<String> response = request("GET", "/congestion", null);

        assertEquals(200, response.statusCode());
        assertEquals("{\"level\":2}", response.body());
    }

    @Test
    void validChangedUpdatePublishesExactlyOnce() throws Exception {
        AtomicInteger publishedLevel = new AtomicInteger(-1);
        startWith(new CongestionLevel(2), publishedLevel::set);

        HttpResponse<String> response = request("PUT", "/congestion", "{\"level\":6}");

        assertEquals(200, response.statusCode());
        assertEquals(6, publishedLevel.get());
        assertTrue(response.body().contains("\"changed\":true"));
        assertTrue(response.body().contains("\"level\":6"));
    }

    @Test
    void invalidUpdateReturnsBadRequestAndDoesNotPublish() throws Exception {
        AtomicInteger publishCount = new AtomicInteger();
        startWith(new CongestionLevel(2), ignored -> publishCount.incrementAndGet());

        HttpResponse<String> response = request("PUT", "/congestion", "{\"level\":9}");

        assertEquals(400, response.statusCode());
        assertEquals(0, publishCount.get());
        assertTrue(response.body().contains("between 0 and 8"));
    }

    private void startWith(CongestionLevel level, java.util.function.IntConsumer publisher) {
        app = CongestionServiceApp.createApp(level, publisher).start(0);
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json");
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
