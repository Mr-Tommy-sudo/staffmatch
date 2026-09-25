package owoke.staffmatch.backend.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import owoke.staffmatch.backend.user.entity.UserAccount;
import owoke.staffmatch.backend.user.entity.UserRole;
import owoke.staffmatch.backend.user.exception.RoleConflictException;
import owoke.staffmatch.backend.user.repository.UserRepository;

class UserServiceTest {

    @Test
    void roleCanBeChosenOnceAndRepeatedWithoutChangingIt() {
        UserRepository repository = mock(UserRepository.class);
        UserAccount user = BeanUtils.instantiateClass(UserAccount.class);
        when(repository.findByMaxUserIdForUpdate(42L)).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);

        assertEquals(UserRole.CANDIDATE, service.chooseRole(42L, UserRole.CANDIDATE).getRole());
        assertEquals(UserRole.CANDIDATE, service.chooseRole(42L, UserRole.CANDIDATE).getRole());
        assertThrows(RoleConflictException.class,
                () -> service.chooseRole(42L, UserRole.EMPLOYER));
        assertEquals(UserRole.CANDIDATE, user.getRole());
    }
}
