package owoke.staffmatch.backend.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PythonHttpClientTest {
    private HttpServer server;

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void retriesTemporaryErrorWithSameIdentifiers() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        List<String> requestIds = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        server.createContext(PythonOperation.MATCHING.path(), exchange -> {
            requestIds.add(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(calls.incrementAndGet() == 1 ? 503 : 200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        var client = new PythonHttpClient("http://127.0.0.1:" + server.getAddress().getPort(),
                new ObjectMapper());
        assertEquals(true, client.call(PythonOperation.MATCHING, Map.of("test", true), "stable-key")
                .path("ok").booleanValue());
        assertEquals(2, calls.get());
        assertEquals(List.of("stable-key", "stable-key"), keys);
        assertEquals(requestIds.get(0), requestIds.get(1));
    }

    @Test
    void doesNotRetryValidationError() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext(PythonOperation.MATCHING.path(), exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"error\":\"VALIDATION_ERROR\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(422, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        var client = new PythonHttpClient("http://127.0.0.1:" + server.getAddress().getPort(),
                new ObjectMapper());
        assertEquals(422, assertThrows(PythonServiceException.class,
                () -> client.call(PythonOperation.MATCHING, Map.of(), "key")).status());
        assertEquals(1, calls.get());
    }

    @Test
    void timesOutWhenPythonDoesNotRespond() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PythonOperation.MATCHING.path(), exchange -> {
            try {
                Thread.sleep(4_000);
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var stream = exchange.getResponseBody()) { stream.write(body); }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                // The client can close the connection after its deadline.
            }
        });
        server.start();
        var client = new PythonHttpClient("http://127.0.0.1:" + server.getAddress().getPort(),
                new ObjectMapper());
        assertEquals(503, assertThrows(PythonServiceException.class,
                () -> client.call(PythonOperation.MATCHING, Map.of(), "timeout-key")).status());
    }
}
