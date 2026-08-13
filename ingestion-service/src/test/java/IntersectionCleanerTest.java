import co.wethinkcode.trafficflow.IntersectionCleaner.Intersection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {IntersectionCleaner} grouped by the data issue each one is responsible for handling as per README.
 */
public class IntersectionCleanerTest {

    @Nested
    public class Casing {
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
    public class PaddingAndWhiteSpaces {

        @Test
        void leadingAndTrailingSpacesAreTrimmed() {
            assertEquals("INT-1001", IntersectionCleaner.cleanId(" INT-1001 "));
            assertEquals("North York", IntersectionCleaner.cleanDistrict(" North York "));
            assertEquals("roundabout", IntersectionCleaner.cleanSignalType(" roundabout "));
        }

        @Test
        void internalDoubleSpacesAreCollapsed() {
            assertEquals("North York", IntersectionCleaner.cleanDistrict("North  York"));
            assertEquals("4-way", IntersectionCleaner.cleanSignalType("4-  way"));
        }

    }

    @Nested
    public class MissingValues {

        @ParameterizedTest  // to run the same test with multiple inputs
        @ValueSource(strings = {"", " ", "N/A", "n/a", "TBD", "tbd", "unknown", "UNKNOWN", "-", "NaN"})
        void placeholderBecomesNullDistrict(String placeholder) {
            assertNull(IntersectionCleaner.cleanDistrict(placeholder));
        }

        @ParameterizedTest  // to run the same test with multiple inputs
        @ValueSource(strings = {"", " ", "N/A", "n/a", "TBD", "tbd", "unknown", "UNKNOWN", "-", "NaN"})
        void placeholderBecomesNullSignalType(String placeholder) {
            assertNull(IntersectionCleaner.cleanSignalType(placeholder));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "N/A", "TBD", "unknown", "-", "NaN"})
        void placeholdersBecomeNullActiveFlag(String raw) {
            assertNull(IntersectionCleaner.cleanActiveFlag(raw));
        }

        @Test
        void unrecognizedActiveFlagValueBecomesNullRatherThanGuessed() {
            assertNull(IntersectionCleaner.cleanActiveFlag("maybe"));
        }

    }

    @Nested
    public class BooleanNormalization {

        @ParameterizedTest
        @ValueSource(strings = {"Y", "y", "yes", "YES", "true", "TRUE", "1"})
        void truthyVariantsBecomeTrue(String raw) {
            assertEquals(Boolean.TRUE, IntersectionCleaner.cleanActiveFlag(raw));
        }

        @ParameterizedTest
        @ValueSource(strings = {"N", "n", "no", "NO", "false", "FALSE", "0"})
        void falsyVariantsBecomeFalse(String raw) {
            assertEquals(Boolean.FALSE, IntersectionCleaner.cleanActiveFlag(raw));
        }
    }

    @Nested
    class DuplicateHandling {

        @Test
        void identicalDuplicatesCollapseCleanly() {
            Intersection a = new Intersection("INT-1005", "Downtown", "roundabout", true);
            Intersection b = new Intersection("INT-1005", "Downtown", "roundabout", true);

            Intersection merged = IntersectionCleaner.mergeDuplicates(a, b);

            assertEquals("Downtown", merged.district());
            assertEquals("roundabout", merged.signalType());
            assertTrue(merged.active());
        }

        @Test
        void missingFieldOnOneSideIsFilledFromTheOther() {
            Intersection missingSignalType = new Intersection("INT-1007", "Eastside", null, true);
            Intersection hasSignalType = new Intersection("INT-1007", "Eastside", "4-way", true);

            Intersection merged = IntersectionCleaner.mergeDuplicates(missingSignalType, hasSignalType);

            assertEquals("4-way", merged.signalType());
        }

        @Test
        void conflictingNonNullValuesKeepTheFirstSeen() {
            Intersection first = new Intersection("INT-1099", "Downtown", "4-way", true);
            Intersection second = new Intersection("INT-1099", "Midtown", "4-way", true);

            Intersection merged = IntersectionCleaner.mergeDuplicates(first, second);

            // First-seen wins on a genuine conflict — see mergeDuplicates' Javadoc.
            assertEquals("Downtown", merged.district());
        }

        @Test
        void twoRowsForSameIntersectionCollapseToOneRecordEndToEnd() {
            String csv = """
                    intersection_id,District,signal_type,active_flag
                    INT-1005,Downtown,Roundabout,true
                    int-1005,downtown ,ROUNDABOUT,TRUE
                    """;

            List<Intersection> result = IntersectionCleaner.cleanCsv(new StringReader(csv));

            assertEquals(1, result.size());
            Intersection only = result.get(0);
            assertEquals("INT-1005", only.id());
            assertEquals("Downtown", only.district());
            assertEquals("roundabout", only.signalType());
            assertTrue(only.active());
        }
    }

    @Nested
    class EndToEnd {

        @Test
        void realBundledCsvCleansWithoutErrorAndDedupes() {
            List<Intersection> result = IntersectionCleaner.loadAndClean("/intersections-legacy.csv");

            // 18 raw data rows in the bundled CSV, one duplicate pair (INT-1005 / int-1005) -> 17 records.
            assertEquals(17, result.size());

            // every record must have an ID; district/signalType/active may legitimately be null
            assertTrue(result.stream().allMatch(i -> i.id() != null && !i.id().isBlank()));

            // IDs must be unique after cleaning, that's the whole point of the dedupe step
            long distinctIds = result.stream().map(Intersection::id).distinct().count();
            assertEquals(result.size(), distinctIds);
        }

        @Test
        void rowWithBlankDistrictKeepsRecordButNullsTheField() {
            List<Intersection> result = IntersectionCleaner.loadAndClean("/intersections-legacy.csv");

            Intersection int1015 = result.stream()
                    .filter(i -> i.id().equals("INT-1015"))
                    .findFirst()
                    .orElseThrow();

            assertNull(int1015.district());
        }
    }
}




}
