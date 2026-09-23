package owoke.staffmatch.backend.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    @Test
    void roleCanBeChosenOnceAndRepeatedWithoutChangingIt() {
        UserRepository repository = mock(UserRepository.class);
        UserAccount user = new UserAccount();
        when(repository.findByMaxUserIdForUpdate(42L)).thenReturn(Optional.of(user));
        UserService service = new UserService(repository);

        assertEquals(UserRole.CANDIDATE, service.chooseRole(42L, UserRole.CANDIDATE).getRole());
        assertEquals(UserRole.CANDIDATE, service.chooseRole(42L, UserRole.CANDIDATE).getRole());
        assertThrows(RoleConflictException.class,
                () -> service.chooseRole(42L, UserRole.EMPLOYER));
        assertEquals(UserRole.CANDIDATE, user.getRole());
    }
}
