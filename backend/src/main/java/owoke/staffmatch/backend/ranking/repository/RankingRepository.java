package owoke.staffmatch.backend.ranking.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import owoke.staffmatch.backend.common.JsonSupport;
import owoke.staffmatch.backend.ranking.entity.RankingEntry;
import tools.jackson.databind.JsonNode;

@Repository
public class RankingRepository {
    private final JdbcClient jdbc;
    private final JsonSupport json;

    public RankingRepository(JdbcClient jdbc, JsonSupport json) {
        this.jdbc = jdbc; this.json = json;
    }

    @Transactional
    public void replace(UUID vacancyId, List<RankingEntry> entries) {
        jdbc.sql("DELETE FROM ranking_entries WHERE vacancy_id = :id").param("id", vacancyId).update();
        for (RankingEntry e : entries) {
            jdbc.sql("""
                    INSERT INTO ranking_entries(vacancy_id, candidate_id, bucket, rank,
                        final_score, state, components, explanation, ranking_version, input_snapshot)
                    VALUES (:vacancy, :candidate, :bucket, :rank, :score, :state,
                        CAST(:components AS jsonb), :explanation, :version, CAST(:snapshot AS jsonb))
                    """).param("vacancy", vacancyId).param("candidate", e.candidateId())
                    .param("bucket", e.bucket()).param("rank", e.rank())
                    .param("score", e.finalScore()).param("state", e.state())
                    .param("components", e.components() == null ? null : json.write(e.components()))
                    .param("explanation", e.explanation()).param("version", e.version())
                    .param("snapshot", json.write(e.inputSnapshot())).update();
        }
    }

    public List<RankingEntry> byVacancy(UUID vacancyId) {
        return jdbc.sql("""
                SELECT * FROM ranking_entries WHERE vacancy_id = :id
                ORDER BY CASE bucket WHEN 'FINAL' THEN 0 ELSE 1 END, rank NULLS LAST, candidate_id
                """).param("id", vacancyId).query((rs, n) -> new RankingEntry(
                        rs.getObject("candidate_id", UUID.class), rs.getString("bucket"),
                        (Integer) rs.getObject("rank"), rs.getObject("final_score") == null ? null :
                                rs.getBigDecimal("final_score").doubleValue(),
                        rs.getString("state"), rs.getString("components") == null ? null :
                                json.read(rs.getString("components")), rs.getString("explanation"),
                        rs.getString("ranking_version"), json.read(rs.getString("input_snapshot")))).list();
    }

    public boolean isFinal(UUID vacancyId, UUID candidateId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM ranking_entries
                WHERE vacancy_id = :vacancy AND candidate_id = :candidate AND bucket = 'FINAL'
                """).param("vacancy", vacancyId).param("candidate", candidateId)
                .query(Integer.class).single() > 0;
    }

}
