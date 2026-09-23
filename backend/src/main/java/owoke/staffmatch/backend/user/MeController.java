package owoke.staffmatch.backend.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import owoke.staffmatch.backend.auth.MaxPrincipal;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public MeResponse getMe(@AuthenticationPrincipal MaxPrincipal principal) {
        return MeResponse.from(userService.getOrCreate(principal.maxUserId()));
    }

    @PutMapping("/role")
    public MeResponse chooseRole(
            @AuthenticationPrincipal MaxPrincipal principal,
            @Valid @RequestBody ChooseRoleRequest request
    ) {
        return MeResponse.from(userService.chooseRole(principal.maxUserId(), request.role()));
    }

    @ExceptionHandler(RoleConflictException.class)
    public ProblemDetail handleRoleConflict(RoleConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    public record MeResponse(UUID id, UserRole role) {
        static MeResponse from(UserAccount user) {
            return new MeResponse(user.getId(), user.getRole());
        }
    }

    public record ChooseRoleRequest(@NotNull UserRole role) {
    }
}
