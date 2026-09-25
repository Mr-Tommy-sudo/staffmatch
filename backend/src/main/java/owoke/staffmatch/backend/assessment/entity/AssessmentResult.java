package owoke.staffmatch.backend.assessment.entity;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record AssessmentResult(UUID assignmentId, double totalScore, JsonNode questions,
                               JsonNode breakdown, String summary, String scoringVersion,
                               String modelVersion) {}
