package owoke.staffmatch.backend.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByMaxUserId(long maxUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.maxUserId = :maxUserId")
    Optional<UserAccount> findByMaxUserIdForUpdate(@Param("maxUserId") long maxUserId);

    @Modifying
    @Query(value = """
            INSERT INTO users (id, max_user_id, created_at)
            VALUES (:id, :maxUserId, CURRENT_TIMESTAMP)
            ON CONFLICT (max_user_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("maxUserId") long maxUserId);
}
