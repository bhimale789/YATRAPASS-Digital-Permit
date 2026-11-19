package com.example.touristpermit.route;

import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.road.Coordinate;
import com.example.touristpermit.support.TestProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the OSRM client against a local stub HTTP server. */
class OsrmRouteProviderTest {

    HttpServer server;
    final AtomicReference<String> lastPath = new AtomicReference<>();
    volatile int status = 200;
    volatile String response = "{}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().toString());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    OsrmRouteProvider provider() {
        AppProperties base = TestProperties.defaults();
        AppProperties props = new AppProperties(base.baseUrl(), base.security(), base.storage(), base.qr(),
                base.verification(), base.permits(), null,
                new AppProperties.Routing("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(3),
                        Duration.ofHours(1), "Kathmandu", 27.7172, 85.3240));
        return new OsrmRouteProvider(RestClient.builder(), props);
    }

    @Test
    void parsesRouteAndUsesLngLatOrder() {
        response = """
                {"code":"Ok","routes":[{"distance":412300.5,"duration":40500.2,
                 "geometry":{"type":"LineString","coordinates":[[85.324,27.7172],[84.0,28.2],[83.723,28.7804]]}}]}""";
        RouteResult r = provider().route(new Coordinate(27.7172, 85.324), new Coordinate(28.7804, 83.723)).orElseThrow();

        assertThat(lastPath.get()).startsWith("/route/v1/driving/85.324000,27.717200;83.723000,28.780400")
                .contains("overview=full").contains("geometries=geojson");
        assertThat(r.distanceMeters()).isEqualTo(412300.5);
        assertThat(r.durationSeconds()).isEqualTo(40500.2);
        assertThat(r.coordinates()).hasSize(3);
        assertThat(r.coordinates().get(0)).isEqualTo(new Coordinate(27.7172, 85.324)); // flipped to lat/lng
    }

    @Test
    void noRouteIsEmpty() {
        status = 400;
        response = "{\"code\":\"NoRoute\",\"message\":\"Impossible route between points\"}";
        assertThat(provider().route(new Coordinate(27.7, 85.3), new Coordinate(-33.9, 151.2))).isEmpty();
    }

    @Test
    void serverErrorsAreUnavailable() {
        status = 502;
        response = "<html>Bad gateway</html>";
        assertThatThrownBy(() -> provider().route(new Coordinate(27.7, 85.3), new Coordinate(28.7, 83.7)))
                .isInstanceOf(RouteUnavailableException.class);
    }

    @Test
    void unreachableServiceIsUnavailable() {
        OsrmRouteProvider provider = provider();
        server.stop(0);
        assertThatThrownBy(() -> provider.route(new Coordinate(27.7, 85.3), new Coordinate(28.7, 83.7)))
                .isInstanceOf(RouteUnavailableException.class);
    }
}
