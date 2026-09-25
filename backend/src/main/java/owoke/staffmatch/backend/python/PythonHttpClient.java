package owoke.staffmatch.backend.python;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class PythonHttpClient implements PythonGateway {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final URI baseUri;
    private final ObjectMapper json;

    public PythonHttpClient(@Value("${python.base-url}") String baseUrl, ObjectMapper json) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.json = json;
    }

    @Override
    public JsonNode call(PythonOperation operation, Object body, String idempotencyKey) {
        String requestId = UUID.randomUUID().toString();
        String requestBody = json.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(operation.path().substring(1)))
                .timeout(operation.timeout())
                .header("Content-Type", "application/json")
                .header("X-Request-Id", requestId)
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    JsonNode result = json.readTree(response.body());
                    if (result == null || !result.isObject()) {
                        throw new PythonServiceException(502, "Python returned invalid JSON");
                    }
                    return result;
                }
                if (!retryable(status) || attempt == 2) {
                    throw new PythonServiceException(status, "Python " + operation + " returned HTTP " + status);
                }
            } catch (IOException exception) {
                if (attempt == 2) {
                    throw new PythonServiceException(503, "Python " + operation + " is unavailable");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PythonServiceException(503, "Python request interrupted");
            }
            try {
                Thread.sleep(200L * (attempt + 1));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new PythonServiceException(503, "Python retry interrupted");
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private static boolean retryable(int status) {
        return status == 429 || status == 500 || status == 503;
    }
}
