package owoke.staffmatch.backend.assessment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import owoke.staffmatch.backend.assessment.entity.TestDefinition;
import owoke.staffmatch.backend.assessment.entity.TestQuestion;

@Repository
public class TestRepository {
    private final JdbcClient jdbc;

    public TestRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<TestDefinition> findByVacancy(UUID vacancyId) {
        return jdbc.sql("SELECT * FROM tests WHERE vacancy_id = :id").param("id", vacancyId)
                .query((rs, n) -> new TestDefinition(rs.getObject("id", UUID.class),
                        rs.getObject("vacancy_id", UUID.class), rs.getString("external_test_id"),
                        rs.getString("generation_version"), rs.getInt("estimated_duration_minutes"),
                        rs.getString("generation_input")))
                .optional();
    }

    public int insert(TestDefinition test) {
        return jdbc.sql("""
                INSERT INTO tests(id, vacancy_id, external_test_id, generation_version,
                    estimated_duration_minutes, generation_input)
                VALUES (:id, :vacancy, :external, :version, :duration, CAST(:input AS jsonb))
                ON CONFLICT (vacancy_id) DO NOTHING
                """).param("id", test.id()).param("vacancy", test.vacancyId())
                .param("external", test.externalTestId()).param("version", test.generationVersion())
                .param("duration", test.estimatedDurationMinutes())
                .param("input", test.generationInput()).update();
    }

    public void insertQuestion(UUID testId, String questionId, int position, String questionJson) {
        jdbc.sql("""
                INSERT INTO test_questions(test_id, question_id, position, question)
                VALUES (:test, :questionId, :position, CAST(:question AS jsonb))
                """).param("test", testId).param("questionId", questionId)
                .param("position", position).param("question", questionJson).update();
    }

    public List<TestQuestion> questions(UUID testId) {
        return jdbc.sql("SELECT question_id, position, question FROM test_questions WHERE test_id = :id ORDER BY position")
                .param("id", testId).query((rs, n) -> new TestQuestion(
                        rs.getString("question_id"), rs.getInt("position"), rs.getString("question"))).list();
    }

}
