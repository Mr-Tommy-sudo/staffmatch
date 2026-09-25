package owoke.staffmatch.backend.invitation.entity;

import java.util.UUID;

public record Invitation(UUID id, UUID vacancyId, UUID candidateId, String status) {}
