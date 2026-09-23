package owoke.staffmatch.backend.user;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserAccount getOrCreate(long maxUserId) {
        repository.insertIfAbsent(UUID.randomUUID(), maxUserId);
        return repository.findByMaxUserId(maxUserId).orElseThrow();
    }

    @Transactional
    public UserAccount chooseRole(long maxUserId, UserRole requestedRole) {
        repository.insertIfAbsent(UUID.randomUUID(), maxUserId);
        UserAccount user = repository.findByMaxUserIdForUpdate(maxUserId).orElseThrow();
        if (user.getRole() != null && user.getRole() != requestedRole) {
            throw new RoleConflictException();
        }
        if (user.getRole() == null) {
            user.setRole(requestedRole);
        }
        return user;
    }
}
