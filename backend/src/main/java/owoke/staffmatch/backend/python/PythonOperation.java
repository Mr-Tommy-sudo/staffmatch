package owoke.staffmatch.backend.python;

import java.time.Duration;

public enum PythonOperation {
    MATCHING("/api/v1/matching/calculate", Duration.ofSeconds(3)),
    GENERATE("/api/v1/tests/generate", Duration.ofSeconds(15)),
    SCORE("/api/v1/tests/score", Duration.ofSeconds(10)),
    RANK("/api/v1/ranking/calculate", Duration.ofSeconds(3));

    private final String path;
    private final Duration timeout;

    PythonOperation(String path, Duration timeout) {
        this.path = path;
        this.timeout = timeout;
    }

    public String path() { return path; }
    public Duration timeout() { return timeout; }
}
