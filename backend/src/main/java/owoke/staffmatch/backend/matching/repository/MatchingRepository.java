package owoke.staffmatch.backend.matching.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import owoke.staffmatch.backend.common.JsonSupport;
import owoke.staffmatch.backend.matching.entity.MatchResult;

@Repository
public class MatchingRepository {
    private final JdbcClient jdbc;
    private final JsonSupport json;

    public MatchingRepository(JdbcClient jdbc, JsonSupport json) {
        this.jdbc = jdbc; this.json = json;
    }

    @Transactional
    public void replace(UUID vacancyId, List<MatchResult> matches) {
        jdbc.sql("DELETE FROM matches WHERE vacancy_id = :id").param("id", vacancyId).update();
        for (MatchResult m : matches) {
            jdbc.sql("""
                    INSERT INTO matches(vacancy_id, candidate_id, eligible, score, matched, partial,
                        missing, algorithm_version, input_snapshot)
                    VALUES (:vacancy, :candidate, :eligible, :score, CAST(:matched AS jsonb),
                        CAST(:partial AS jsonb), CAST(:missing AS jsonb), :version,
                        CAST(:snapshot AS jsonb))
                    """).param("vacancy", vacancyId).param("candidate", m.candidateId())
                    .param("eligible", m.eligible()).param("score", m.score())
                    .param("matched", json.write(m.matched())).param("partial", json.write(m.partial()))
                    .param("missing", json.write(m.missing())).param("version", m.algorithmVersion())
                    .param("snapshot", json.write(m.inputSnapshot())).update();
        }
    }

    public List<MatchResult> byVacancy(UUID vacancyId) {
        return jdbc.sql("SELECT * FROM matches WHERE vacancy_id = :id ORDER BY score DESC, candidate_id")
                .param("id", vacancyId).query((rs, n) -> new MatchResult(
                        rs.getObject("candidate_id", UUID.class), rs.getBoolean("eligible"),
                        rs.getDouble("score"), json.read(rs.getString("matched")),
                        json.read(rs.getString("partial")), json.read(rs.getString("missing")),
                        rs.getString("algorithm_version"), json.read(rs.getString("input_snapshot")))).list();
    }

}
