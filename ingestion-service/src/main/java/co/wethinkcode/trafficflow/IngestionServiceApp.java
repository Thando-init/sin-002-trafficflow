package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.io.IOException;
import java.util.List;

public class IngestionServiceApp {

    /** Starts the service, initializes its dependencies, and binds the HTTP API to the configured port.
     */
    public static void main(String[] args) throws IOException {
        List<IntersectionCleaner.Intersection> intersections = IntersectionCleaner.loadAndClean("/intersections-legacy.csv");

        createApp(intersections).start(7020);
    }

    /**
     * Builds the HTTP application around a supplied immutable snapshot.
     * Keeping construction separate from {@link #main(String[])} makes the routes
     * testable on an ephemeral port without binding the production port.
     *
     * @param intersections records to return from the catalogue endpoint
     * @return configured but not yet started Javalin application
     */
    static Javalin createApp(List<IntersectionCleaner.Intersection> intersections) {
        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson()));
        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/intersections", ctx -> ctx.json(intersections));
        return app;
    }

//    public static void main(String[] args) throws IOException {
//        List<IntersectionCleaner.Intersection> intersections = IntersectionCleaner.loadAndClean("/intersections-legacy.csv");
//
//        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson())).start(7020);
//
//        app.get("/health", ctx -> ctx.result("OK"));
//
//        // TODO: read and clean src/main/resources/intersections-legacy.csv (intersections, districts, signal types data —
//        // trim whitespace, fix casing, normalize dates/booleans) and expose the
//        // cleaned records here for the other services to consume.
//        app.get("/intersections", ctx -> ctx.json(intersections));
//
//
//    }
}
