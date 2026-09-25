package owoke.staffmatch.backend.python;

import tools.jackson.databind.JsonNode;

public interface PythonGateway {
    JsonNode call(PythonOperation operation, Object body, String idempotencyKey);
}
