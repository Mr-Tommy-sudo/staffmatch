package owoke.staffmatch.backend.assessment.entity;

import java.util.UUID;

public record TestAssignment(UUID id, UUID vacancyId, UUID candidateId, String status,
                             String answers, String scoringInput, String lastError) {}
