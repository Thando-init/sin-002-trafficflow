package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.io.IOException;
import java.util.List;

public class IngestionServiceApp {

    public static void main(String[] args) throws IOException {
        List<IntersectionCleaner.Intersection> intersections = IntersectionCleaner.loadAndClean("/intersections-legacy.csv");

        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson())).start(7020);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/intersections-legacy.csv (intersections, districts, signal types data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.
        app.get("/intersections", ctx -> ctx.json(intersections));


    }
}
