package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectionCatalogTest {
    private IntersectionCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new IntersectionCatalog(HttpClient.newHttpClient(), new ObjectMapper(), URI.create("http://localhost:7020/intersections"));
        catalog.replaceAll(List.of(
                new Intersection("int-1001", "Downtown", "4-way", true),
                new Intersection("INT-1002", "North York", "roundabout", true),
                new Intersection("INT-1003", "Downtown", null, false)
        ));
    }

    @Test
    void lookupIsCaseInsensitiveAndUsesCanonicalId() {
        Intersection found = catalog.findById(" Int-1001 ").orElseThrow();

        assertEquals("INT-1001", found.id());
        assertEquals(3, catalog.size());
    }

    @Test
    void validatesDistrictsIgnoringCaseAndWhitespace() {
        assertTrue(catalog.districtExists(" downtown "));
        assertTrue(catalog.districtExists("north   york"));
        assertFalse(catalog.districtExists("Lakeside"));
    }

    @Test
    void returnsOnlyRequestedDistrictInStableOrder() {
        List<Intersection> downtown = catalog.findByDistrict("DOWNTOWN");

        assertEquals(List.of("INT-1001", "INT-1003"), downtown.stream().map(Intersection::id).toList());
    }

    @Test
    void replacementIgnoresUnidentifiableRecords() {
        catalog.replaceAll(Arrays.asList(
                new Intersection("INT-2001", "Midtown", "4-way", true),
                new Intersection(" ", "Unknown", "4-way", true),
                null
        ));

        assertEquals(1, catalog.size());
        assertTrue(catalog.findById("INT-2001").isPresent());
    }
}
