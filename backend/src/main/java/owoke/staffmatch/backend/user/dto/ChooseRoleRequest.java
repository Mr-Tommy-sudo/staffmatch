package owoke.staffmatch.backend.user.dto;

import jakarta.validation.constraints.NotNull;
import owoke.staffmatch.backend.user.entity.UserRole;

public record ChooseRoleRequest(@NotNull UserRole role) {
}
