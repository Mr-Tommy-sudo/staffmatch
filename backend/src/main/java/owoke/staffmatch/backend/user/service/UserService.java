package owoke.staffmatch.backend.user.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import owoke.staffmatch.backend.user.entity.UserAccount;
import owoke.staffmatch.backend.user.entity.UserRole;
import owoke.staffmatch.backend.user.repository.UserRepository;

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
        user.chooseRole(requestedRole);
        return user;
    }
}
