package owoke.staffmatch.backend.common;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JsonSupport {
    private final ObjectMapper json;

    public JsonSupport(ObjectMapper json) { this.json = json; }

    public String write(Object value) { return json.writeValueAsString(value); }
    public JsonNode read(String value) { return json.readTree(value); }
    public JsonNode tree(Object value) { return read(write(value)); }

    public String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Python response missing " + field);
        }
        return value.asText();
    }

    public double score(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber() || value.doubleValue() < 0 || value.doubleValue() > 100) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned invalid " + field);
        }
        return value.doubleValue();
    }

    public JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Python response missing " + field);
        }
        return value;
    }

    public Map<String, Object> error(String status, String message) {
        return Map.of("status", status, "message", message);
    }
}
