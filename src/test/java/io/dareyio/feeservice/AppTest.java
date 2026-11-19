package io.dareyio.feeservice;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts the real HTTP server on a random port and exercises the endpoints. */
class AppTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() throws Exception {
        server = App.start(0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthReturnsUp() throws Exception {
        HttpResponse<String> res = get("/health");
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"status\":\"UP\""), res.body());
    }

    @Test
    void feeEndpointReturnsFeeAndTotal() throws Exception {
        HttpResponse<String> res = get("/api/fee?amount=1000");
        assertEquals(200, res.statusCode());
        assertEquals("{\"amount\":\"1000\",\"fee\":\"10.75\",\"total\":\"1010.75\"}", res.body());
    }

    @Test
    void nonNumericAmountIsBadRequest() throws Exception {
        assertEquals(400, get("/api/fee?amount=abc").statusCode());
    }

    @Test
    void missingAmountIsBadRequest() throws Exception {
        assertEquals(400, get("/api/fee").statusCode());
    }

    @Test
    void negativeAmountIsBadRequest() throws Exception {
        assertEquals(400, get("/api/fee?amount=-50").statusCode());
    }
}
