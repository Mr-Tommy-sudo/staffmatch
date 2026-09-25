package owoke.staffmatch.backend.ranking.entity;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record RankingEntry(UUID candidateId, String bucket, Integer rank, Double finalScore,
                           String state, JsonNode components, String explanation,
                           String version, JsonNode inputSnapshot) {}
