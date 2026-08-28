package co.wethinkcode.trafficflow;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cleans the legacy intersection export independently from the HTTP layer.
 * Missing source values are represented explicitly as {@code null}, rather
 * than being guessed or silently discarded.
 */
public final class IntersectionCleaner {
    private static final Set<String> MISSING_PLACEHOLDERS = Set.of("", "n/a", "tbd", "unknown", "-", "nan");
    private static final Set<String> TRUE_VALUES = Set.of("true", "yes", "1", "y");
    private static final Set<String> FALSE_VALUES = Set.of("false", "no", "0", "n", "f");

    /** Documents the IntersectionCleaner operation and its effect on service state or external communication.
     */
    private IntersectionCleaner() {
    }

    /** A cleaned intersection whose non-ID fields may be unknown. */
    public record Intersection(String id, String district, String signalType, Boolean active) {
    }

    /**
     * Reads and cleans a CSV resource available on the application's classpath.
     *
     * @param classpathResource an absolute classpath resource path
     * @return cleaned, de-duplicated intersections in source order
     * @throws IOException if the resource cannot be found or read
     */
    /** Loads the legacy CSV resource from the classpath, cleans its rows, and returns records in source order.
     * @param classpathResource absolute classpath resource path
     * @return cleaned, de-duplicated intersection records
     * @throws IOException when the resource is missing or cannot be read.
     */
    public static List<Intersection> loadAndClean(String classpathResource) throws IOException {
        InputStream input = IntersectionCleaner.class.getResourceAsStream(classpathResource);
        if (input == null) {
            throw new IOException("Classpath resource not found: " + classpathResource);
        }
        try (input; Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return cleanCsv(reader);
        }
    }

    /**
     * Reads a legacy CSV from a reader, normalizes fields, and collapses duplicate IDs.
     * Rows without a usable ID are ignored because they cannot be identified downstream.
     */
    public static List<Intersection> cleanCsv(Reader source) {
        Map<String, Intersection> byId = new LinkedHashMap<>();

        try (CSVReader reader = new CSVReader(source)) {
            String[] header = reader.readNext();
            if (header == null || header.length == 0) {
                return Collections.emptyList();
            }

            String[] row;
            while ((row = reader.readNext()) != null) {
                Intersection cleaned = cleanRow(row);
                if (cleaned != null) {
                    byId.merge(cleaned.id(), cleaned, IntersectionCleaner::mergeDuplicates);
                }
            }
        } catch (IOException | CsvValidationException exception) {
            throw new IllegalArgumentException("Failed to parse intersection CSV", exception);
        }

        return new ArrayList<>(byId.values());
    }

    /** Converts one raw CSV row into a cleaned record, or returns null when its identifier cannot be established.
     */
    static Intersection cleanRow(String[] row) {
        if (row == null || row.length < 4) {
            return null;
        }

        String id = cleanId(row[0]);
        if (id.isEmpty()) {
            return null;
        }
        return new Intersection(id, cleanDistrict(row[1]), cleanSignalType(row[2]), cleanActiveFlag(row[3]));
    }

    /** Normalizes an identifier by trimming, collapsing whitespace, and converting it to uppercase.
     */
    static String cleanId(String raw) {
        String normalized = normalizeWhitespace(raw);
        return isMissing(normalized) ? "" : normalized.toUpperCase(Locale.ROOT);
    }

    /** Normalizes a district name to whitespace-cleaned title case, preserving missing values as null.
     */
    static String cleanDistrict(String raw) {
        String normalized = normalizeWhitespace(raw);
        return isMissing(normalized) ? null : titleCase(normalized);
    }

    /** Normalizes a signal type to lowercase, preserving missing values as null.
     */
    static String cleanSignalType(String raw) {
        String normalized = normalizeWhitespace(raw);
        return isMissing(normalized) ? null : normalized.toLowerCase(Locale.ROOT);
    }

    /** Maps supported textual and numeric flag representations to Boolean and treats unknown values as null.
     */
    static Boolean cleanActiveFlag(String raw) {
        String normalized = normalizeWhitespace(raw).toLowerCase(Locale.ROOT);
        if (isMissing(normalized)) {
            return null;
        }
        if (TRUE_VALUES.contains(normalized)) {
            return Boolean.TRUE;
        }
        if (FALSE_VALUES.contains(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** Combines duplicate records field by field, preferring known values and retaining the first value on conflicts.
     */
    static Intersection mergeDuplicates(Intersection first, Intersection second) {
        String district = pickField(first.id(), "district", first.district(), second.district());
        String signalType = pickField(first.id(), "signalType", first.signalType(), second.signalType());
        Boolean active = pickField(first.id(), "active", first.active(), second.active());
        return new Intersection(first.id(), district, signalType, active);
    }

    /** Documents the pickField operation and its effect on service state or external communication.
     */
    private static <T> T pickField(String id, String fieldName, T existing, T incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null || existing.equals(incoming)) {
            return existing;
        }
        System.err.printf(
                "WARNING: duplicate %s has conflicting %s values (%s vs %s); retaining first value.%n",
                id, fieldName, existing, incoming
        );
        return existing;
    }

    /** Tests whether a normalized value is one of the configured source placeholders.
     */
    static boolean isMissing(String normalized) {
        return MISSING_PLACEHOLDERS.contains(normalized.toLowerCase(Locale.ROOT));
    }

    /** Trims a value and collapses every run of whitespace to one space.
     */
    static String normalizeWhitespace(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    /** Converts each whitespace-delimited word to title case.
     */
    static String titleCase(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalizeNext = true;
        for (char character : value.toCharArray()) {
            if (Character.isWhitespace(character)) {
                capitalizeNext = true;
                result.append(character);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(character));
            }
        }
        return result.toString();
    }
}
