package owoke.staffmatch.backend.assessment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import owoke.staffmatch.backend.assessment.entity.TestAssignment;

@Repository
public class AssignmentRepository {
    private final JdbcClient jdbc;

    public AssignmentRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void create(UUID id, UUID vacancy, UUID candidate) {
        jdbc.sql("""
                INSERT INTO test_assignments(id, vacancy_id, candidate_id, status)
                VALUES (:id, :vacancy, :candidate, 'ASSIGNED')
                ON CONFLICT (vacancy_id, candidate_id) DO NOTHING
                """).param("id", id).param("vacancy", vacancy).param("candidate", candidate).update();
    }

    public Optional<TestAssignment> find(UUID id) {
        return jdbc.sql("SELECT * FROM test_assignments WHERE id = :id").param("id", id)
                .query((rs, n) -> map(rs)).optional();
    }

    public Optional<TestAssignment> find(UUID vacancy, UUID candidate) {
        return jdbc.sql("SELECT * FROM test_assignments WHERE vacancy_id = :vacancy AND candidate_id = :candidate")
                .param("vacancy", vacancy).param("candidate", candidate)
                .query((rs, n) -> map(rs)).optional();
    }

    public List<TestAssignment> byCandidate(UUID candidate) {
        return jdbc.sql("SELECT * FROM test_assignments WHERE candidate_id = :candidate ORDER BY created_at DESC")
                .param("candidate", candidate).query((rs, n) -> map(rs)).list();
    }

    public List<TestAssignment> byVacancy(UUID vacancy) {
        return jdbc.sql("SELECT * FROM test_assignments WHERE vacancy_id = :vacancy ORDER BY created_at DESC")
                .param("vacancy", vacancy).query((rs, n) -> map(rs)).list();
    }

    public int submit(UUID id, String answers, String scoringInput) {
        return jdbc.sql("""
                UPDATE test_assignments SET status = 'SUBMITTED', answers = CAST(:answers AS jsonb),
                    scoring_input = CAST(:input AS jsonb), submitted_at = CURRENT_TIMESTAMP, last_error = NULL
                WHERE id = :id AND status = 'ASSIGNED'
                """).param("id", id).param("answers", answers).param("input", scoringInput).update();
    }

    public void status(UUID id, String status, String error) {
        jdbc.sql("UPDATE test_assignments SET status = :status, last_error = :error WHERE id = :id")
                .param("id", id).param("status", status).param("error", error).update();
    }

    private static TestAssignment map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TestAssignment(rs.getObject("id", UUID.class), rs.getObject("vacancy_id", UUID.class),
                rs.getObject("candidate_id", UUID.class), rs.getString("status"),
                rs.getString("answers"), rs.getString("scoring_input"), rs.getString("last_error"));
    }

}
