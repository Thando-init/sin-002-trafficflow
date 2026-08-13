package co.wethinkcode.trafficflow;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Pure cleaning logic for {intersections-legacy.csv},
 * free of any HTTP/Javalin concerns so it can be tested in isolation.
 */
public class IntersectionCleaner {
    /**
     * A cleaned intersection record with all fields in the correct format and no missing values.
     * district, signaltype and active(except for id) are nullable because they are required fields,
     *  a missing/placeholder value in the source data is
     *     exposed as an explicit null rather than guessed at or silently dropped,
     *     so downstream services can see what's actually missing.
     */

    public record Intersection(String id, String district, String signalType, Boolean active){
        // record of string values, with id, district, signalType, and active fields. All fields are nullable except for id.
    }

    // Placeholder values the source data uses to indicate a missing value. These are treated as nulls in the cleaned output.
    // matched case sensitively after trimming
    private static final Set<String> MISSING_PLACEHOLDERS = Set.of("", "n/a", "tbd", "unknown", "-", "nan");
    private static final Set<String> TRUE_VALUES = Set.of("true", "yes", "1", "y");
    private static final Set<String> FALSE_VALUES = Set.of("f", "false", "0", "n", "no");

    private IntersectionCleaner(){
        //static utility class not meant to have instances.
    }

    /*
    * Reads the raw CSV off the classpath, cleans every field, and collapses dupes into a single record per intersection id.
     */
    public static List<Intersection> loadAndClean(String classpathResource) throws IOException {
        try (InputStream in = IntersectionCleaner.class.getResourceAsStream(classpathResource)){
            if (in == null){
                throw new IOException("Classpath resource not found: " + classpathResource);
            }
            return cleanCsv(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        catch (IOException e){
            throw new RuntimeException("Failed to load and clean CSV from classpath resource: " + classpathResource, e);
        }
    }

    /**
     * Reads the raw CSV from the given Reader, cleans every field, and collapses dupes into a single record per intersection id.
     */
    public static List<Intersection> cleanCsv(Reader source){
        // Linked HashMap to preserve insertion order while deduplicating by intersection id
        // detect and merge duplicate IDs as we go instead of collecting and then deduplicating in a second pass
        Map<String, Intersection> intersections = new LinkedHashMap<>();

        try (CSVReader reader = new CSVReader(source)){
            String[] header = reader.readNext(); // discard header row
            if (header == null || header.length == 0){
                return Collections.emptyList(); // empty CSV
            }

            String[] row;
            while ((row = reader.readNext()) != null){
                Intersection cleaned = cleanRow(row);
                if (cleaned == null){
                    continue; // skip rows that are completely empty or invalid
                }
                byId.merge(cleaned.id(), cleaned, IntersectionCleaner::mergeDuplicates);
            }

        } catch (IOException | com.opencsv.exceptions.CsvValidationException e) {
            throw new RuntimeException("Failed to parse CSV", e);
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * Cleans a single raw CSV row (as produced by OpenCSV) into an
     * {@link Intersection}, or returns {@code null} if the row is unusable
     * (too few columns, or no identifiable ID).
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



}
