package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** HTTP implementation of intersection lookup for the synchronous stage. */
public final class HttpIntersectionClient implements RouteDependencies.IntersectionClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;

    /** Documents the HttpIntersectionClient operation and its effect on service state or external communication.
     */
    public HttpIntersectionClient(HttpClient httpClient, ObjectMapper objectMapper, URI baseUri) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
    }

    @Override
    /** Calls the intersection service and translates HTTP responses into an optional validated record.
     */
    public Optional<IntersectionDetails> findById(String id) {
        URI endpoint = baseUri.resolve("/intersections/" + URLEncoder.encode(id, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(REQUEST_TIMEOUT).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                throw new DependencyUnavailableException("Intersection service returned HTTP " + response.statusCode());
            }
            return Optional.of(objectMapper.readValue(response.body(), IntersectionDetails.class));
        } catch (IOException exception) {
            throw new DependencyUnavailableException("Intersection service is unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("Intersection service request was interrupted", exception);
        }
    }
}
