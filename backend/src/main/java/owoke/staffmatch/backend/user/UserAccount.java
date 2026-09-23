package owoke.staffmatch.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(name = "max_user_id", nullable = false, unique = true)
    private long maxUserId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private UserRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public UUID getId() {
        return id;
    }

    public long getMaxUserId() {
        return maxUserId;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void setRole(UserRole role) {
        this.role = role;
    }
}
