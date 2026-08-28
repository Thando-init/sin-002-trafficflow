package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.IntersectionCleaner.Intersection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectionCleanerTest {

    @Nested
    class Casing {
        @Test
        void idIsUppercased() {
            assertEquals("INT-1002", IntersectionCleaner.cleanId("int-1002"));
        }

        @Test
        void districtIsTitleCased() {
            assertEquals("North York", IntersectionCleaner.cleanDistrict("north york"));
            assertEquals("Downtown", IntersectionCleaner.cleanDistrict("downtown"));
        }

        @Test
        void signalTypeIsLowercased() {
            assertEquals("roundabout", IntersectionCleaner.cleanSignalType("ROUNDABOUT"));
            assertEquals("4-way", IntersectionCleaner.cleanSignalType("4-WAY"));
        }
    }

    @Nested
    class Whitespace {
        @Test
        void leadingTrailingAndInternalWhitespaceAreNormalized() {
            assertEquals("INT-1001", IntersectionCleaner.cleanId(" INT-1001 "));
            assertEquals("North York", IntersectionCleaner.cleanDistrict(" North   York "));
            assertEquals("4- way", IntersectionCleaner.cleanSignalType(" 4-  way "));
        }
    }

    @Nested
    class MissingValues {
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "N/A", "n/a", "TBD", "tbd", "unknown", "UNKNOWN", "-", "NaN"})
        void placeholdersBecomeExplicitNulls(String placeholder) {
            assertNull(IntersectionCleaner.cleanDistrict(placeholder));
            assertNull(IntersectionCleaner.cleanSignalType(placeholder));
            assertNull(IntersectionCleaner.cleanActiveFlag(placeholder));
        }

        @Test
        void missingIdCausesRowToBeDiscarded() {
            assertNull(IntersectionCleaner.cleanRow(new String[] {"N/A", "Downtown", "4-way", "Y"}));
        }

        @Test
        void unrecognizedActiveFlagBecomesNullRatherThanGuessed() {
            assertNull(IntersectionCleaner.cleanActiveFlag("maybe"));
        }
    }

    @Nested
    class BooleanNormalization {
        @ParameterizedTest
        @ValueSource(strings = {"Y", "y", "yes", "YES", "true", "TRUE", "1"})
        void truthyVariantsBecomeTrue(String raw) {
            assertEquals(Boolean.TRUE, IntersectionCleaner.cleanActiveFlag(raw));
        }

        @ParameterizedTest
        @ValueSource(strings = {"N", "n", "no", "NO", "false", "FALSE", "0", "F"})
        void falsyVariantsBecomeFalse(String raw) {
            assertEquals(Boolean.FALSE, IntersectionCleaner.cleanActiveFlag(raw));
        }
    }

    @Nested
    class DuplicateHandling {

        @Test
        void missingValuesAreFilledFromDuplicateAndConflictsKeepFirstSeenValue() {
            Intersection first = new Intersection("INT-1007", "Eastside", null, true);
            Intersection second = new Intersection("INT-1007", "Midtown", "4-way", true);

            Intersection merged = IntersectionCleaner.mergeDuplicates(first, second);

            assertEquals("Eastside", merged.district());
            assertEquals("4-way", merged.signalType());
            assertTrue(merged.active());
        }

        @Test
        void duplicateRowsCollapseToOneRecordEndToEnd() {
            String csv = """
                    intersection_id,District,signal_type,active_flag
                    INT-1005,Downtown,Roundabout,true
                    int-1005,downtown ,ROUNDABOUT,TRUE
                    """;

            List<Intersection> result = IntersectionCleaner.cleanCsv(new StringReader(csv));

            assertEquals(1, result.size());
            assertEquals(new Intersection("INT-1005", "Downtown", "roundabout", true), result.get(0));
        }
    }

    @Nested
    class EndToEnd {
        @Test
        void bundledCsvCleansWithoutErrorAndProducesUniqueIds() throws Exception {
            List<Intersection> result = IntersectionCleaner.loadAndClean("/intersections-legacy.csv");

            assertEquals(17, result.size());
            assertTrue(result.stream().allMatch(intersection -> !intersection.id().isBlank()));
            assertEquals(result.size(), result.stream().map(Intersection::id).distinct().count());
        }

        @Test
        void bundledCsvPreservesRecordsWithExplicitlyMissingFields() throws Exception {
            List<Intersection> result = IntersectionCleaner.loadAndClean("/intersections-legacy.csv");

            Intersection int1015 = result.stream()
                    .filter(intersection -> intersection.id().equals("INT-1015"))
                    .findFirst()
                    .orElseThrow();
            assertNull(int1015.district());
        }
    }
}
