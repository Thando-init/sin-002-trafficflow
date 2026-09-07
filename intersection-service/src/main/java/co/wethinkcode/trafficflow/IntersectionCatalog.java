package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Collections.replaceAll;

/**
 * Maintains the source of truth of intersection list supplied by the ingestion service.
 * A failed refresh doesnt erase the most recently known good catalogue.
 */
public class IntersectionCatalog {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI ingestionEndpoint; //Java URI Class, which represents and parses web links or resource identifiers
    private volatile Map<String, Intersection> intersectionsById = Map.of(); // guarantees thread-safety for visibility and ordering (volatile) when accessed by multiple threads


    public IntersectionCatalog(HttpClient httpClient, ObjectMapper objectMapper, URI ingestionEndpoint) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.ingestionEndpoint = Objects.requireNonNull(ingestionEndpoint, "ingestionEndpoint");
    }

    public synchronized int refresh() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(ingestionEndpoint)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ingestion service returned HTTP " + response.statusCode());
        }

        List<Intersection> downloaded = objectMapper.readValue(response.body(), new TypeReference<>() { });
        replaceAll(downloaded);
        return intersectionsById.size();
    }

    public synchronized void replaceAll(Collection<Intersection> intersections) {
        Map<String, Intersection> cleaned = new LinkedHashMap<>();
        for (Intersection intersection : intersections) {
            if (intersection == null || intersection.id() == null || intersection.id().isBlank()) {
                continue;
            }
            String normalizedId = normalizeId(intersection.id());
            cleaned.put(normalizedId, new Intersection(normalizedId, intersection.district(),
                    intersection.signalType(), intersection.active()));
        }
        intersectionsById = Map.copyOf(cleaned);
    }

    public Optional<Intersection> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(intersectionsById.get(normalizeId(id)));
    }

    public boolean districtExists(String district) {
        if (district == null || district.isBlank()) {
            return false;
        }
        String normalizedDistrict = normalizeDistrict(district);
        return intersectionsById.values().stream()
                .map(Intersection::district)
                .filter(Objects::nonNull)
                .anyMatch(value -> normalizeDistrict(value).equals(normalizedDistrict));
    }

    public List<Intersection> findByDistrict(String district) {
        if (district == null || district.isBlank()) {
            return List.of();
        }
        String normalizedDistrict = normalizeDistrict(district);
        return intersectionsById.values().stream()
                .filter(intersection -> intersection.district() != null)
                .filter(intersection -> normalizeDistrict(intersection.district()).equals(normalizedDistrict))
                .sorted(Comparator.comparing(Intersection::id))
                .toList();
    }

    public List<Intersection> all() {
        return intersectionsById.values().stream()
                .sorted(Comparator.comparing(Intersection::id))
                .toList();
    }

    public int size() {
        return intersectionsById.size();
    }

    private static String normalizeId(String id) {
        return id.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeDistrict(String district) {
        return district.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }


}
