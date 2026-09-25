package owoke.staffmatch.backend.common;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import owoke.staffmatch.backend.auth.model.MaxPrincipal;
import owoke.staffmatch.backend.user.entity.UserAccount;
import owoke.staffmatch.backend.user.entity.UserRole;
import owoke.staffmatch.backend.user.service.UserService;

@Service
public class AccessService {
    private final UserService users;

    public AccessService(UserService users) { this.users = users; }

    public UUID require(MaxPrincipal principal, UserRole role) {
        UserAccount user = users.getOrCreate(principal.maxUserId());
        if (user.getRole() != role) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Role " + role + " is required");
        }
        return user.getId();
    }
}
