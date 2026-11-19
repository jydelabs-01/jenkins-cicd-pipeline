package io.dareyio.feeservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Minimal HTTP service (JDK built-in server, no framework dependencies).
 *
 *   GET /health                -> {"status":"UP","version":"1.0.0"}
 *   GET /api/fee?amount=1000   -> {"amount":"1000","fee":"10.75","total":"1010.75"}
 */
public class App {

    private static final FeeCalculator CALCULATOR = new FeeCalculator();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("APP_PORT", "8085"));
        HttpServer server = start(port);
        System.out.printf("fee-service %s listening on port %d%n", version(), server.getAddress().getPort());
    }

    /** Starts the server; pass port 0 to bind a random free port (used by tests). */
    public static HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", App::handleHealth);
        server.createContext("/api/fee", App::handleFee);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        return server;
    }

    static String version() {
        String v = App.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    private static void handleHealth(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        respond(ex, 200, "{\"status\":\"UP\",\"version\":\"" + version() + "\"}");
    }

    private static void handleFee(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            respond(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        String raw = queryParams(ex.getRequestURI().getRawQuery()).get("amount");
        try {
            BigDecimal amount = new BigDecimal(raw == null ? "" : raw.trim());
            BigDecimal fee = CALCULATOR.feeFor(amount);
            BigDecimal total = CALCULATOR.totalDebit(amount);
            respond(ex, 200, String.format("{\"amount\":\"%s\",\"fee\":\"%s\",\"total\":\"%s\"}",
                    amount.toPlainString(), fee.toPlainString(), total.toPlainString()));
        } catch (NumberFormatException e) {
            respond(ex, 400, "{\"error\":\"amount must be a number\"}");
        } catch (IllegalArgumentException e) {
            respond(ex, 400, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    static Map<String, String> queryParams(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                params.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                           URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
