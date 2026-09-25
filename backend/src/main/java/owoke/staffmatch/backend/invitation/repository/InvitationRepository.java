package owoke.staffmatch.backend.invitation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import owoke.staffmatch.backend.invitation.entity.Invitation;

@Repository
public class InvitationRepository {
    private final JdbcClient jdbc;

    public InvitationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void create(UUID id, UUID vacancy, UUID candidate) {
        jdbc.sql("""
                INSERT INTO invitations(id, vacancy_id, candidate_id, status)
                VALUES (:id, :vacancy, :candidate, 'PENDING')
                ON CONFLICT (vacancy_id, candidate_id) DO NOTHING
                """).param("id", id).param("vacancy", vacancy).param("candidate", candidate).update();
    }

    public Optional<Invitation> find(UUID id) {
        return jdbc.sql("SELECT * FROM invitations WHERE id = :id").param("id", id)
                .query((rs, n) -> map(rs)).optional();
    }

    public Optional<Invitation> find(UUID vacancy, UUID candidate) {
        return jdbc.sql("SELECT * FROM invitations WHERE vacancy_id = :vacancy AND candidate_id = :candidate")
                .param("vacancy", vacancy).param("candidate", candidate)
                .query((rs, n) -> map(rs)).optional();
    }

    public List<Invitation> byCandidate(UUID candidate) {
        return jdbc.sql("SELECT * FROM invitations WHERE candidate_id = :id ORDER BY created_at DESC")
                .param("id", candidate).query((rs, n) -> map(rs)).list();
    }

    public int decide(UUID id, String status) {
        return jdbc.sql("""
                UPDATE invitations SET status = :status, decided_at = CURRENT_TIMESTAMP
                WHERE id = :id AND status = 'PENDING'
                """).param("id", id).param("status", status).update();
    }

    private static Invitation map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Invitation(rs.getObject("id", UUID.class),
                rs.getObject("vacancy_id", UUID.class), rs.getObject("candidate_id", UUID.class),
                rs.getString("status"));
    }

}
