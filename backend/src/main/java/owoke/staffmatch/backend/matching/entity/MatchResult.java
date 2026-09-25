package owoke.staffmatch.backend.matching.entity;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record MatchResult(UUID candidateId, boolean eligible, double score, JsonNode matched,
                          JsonNode partial, JsonNode missing, String algorithmVersion,
                          JsonNode inputSnapshot) {}
