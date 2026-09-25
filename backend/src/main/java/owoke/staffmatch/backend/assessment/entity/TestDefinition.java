package owoke.staffmatch.backend.assessment.entity;

import java.util.UUID;

public record TestDefinition(UUID id, UUID vacancyId, String externalTestId,
                             String generationVersion, int estimatedDurationMinutes,
                             String generationInput) {}
