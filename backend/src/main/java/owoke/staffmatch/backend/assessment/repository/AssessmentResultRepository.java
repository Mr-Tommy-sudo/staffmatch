package owoke.staffmatch.backend.assessment.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import owoke.staffmatch.backend.common.JsonSupport;
import owoke.staffmatch.backend.assessment.entity.AssessmentResult;
import tools.jackson.databind.JsonNode;

@Repository
public class AssessmentResultRepository {
    private final JdbcClient jdbc;
    private final JsonSupport json;

    public AssessmentResultRepository(JdbcClient jdbc, JsonSupport json) {
        this.jdbc = jdbc; this.json = json;
    }

    @Transactional
    public void save(UUID assignmentId, double score, JsonNode questions, JsonNode breakdown,
                     String summary, String version, String modelVersion) {
        jdbc.sql("""
                INSERT INTO assessment_results(assignment_id, total_score, questions, breakdown,
                    summary, scoring_version, model_version)
                VALUES (:id, :score, CAST(:questions AS jsonb), CAST(:breakdown AS jsonb),
                    :summary, :version, :model)
                ON CONFLICT (assignment_id) DO NOTHING
                """).param("id", assignmentId).param("score", score)
                .param("questions", json.write(questions)).param("breakdown", json.write(breakdown))
                .param("summary", summary).param("version", version).param("model", modelVersion).update();
        jdbc.sql("UPDATE test_assignments SET status = 'SCORED', last_error = NULL WHERE id = :id")
                .param("id", assignmentId).update();
    }

    public Optional<AssessmentResult> find(UUID assignmentId) {
        return jdbc.sql("SELECT * FROM assessment_results WHERE assignment_id = :id")
                .param("id", assignmentId).query((rs, n) -> new AssessmentResult(
                        rs.getObject("assignment_id", UUID.class), rs.getDouble("total_score"),
                        json.read(rs.getString("questions")), json.read(rs.getString("breakdown")),
                        rs.getString("summary"), rs.getString("scoring_version"),
                        rs.getString("model_version"))).optional();
    }

}
