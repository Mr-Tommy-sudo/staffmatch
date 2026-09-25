package owoke.staffmatch.backend.user.dto;

import java.util.UUID;
import owoke.staffmatch.backend.user.entity.UserRole;

public record MeResponse(UUID id, UserRole role) {
}
