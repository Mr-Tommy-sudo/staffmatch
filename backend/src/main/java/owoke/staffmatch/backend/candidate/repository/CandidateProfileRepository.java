package owoke.staffmatch.backend.candidate.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import owoke.staffmatch.backend.candidate.dto.CandidateProfileRequest;
import owoke.staffmatch.backend.candidate.entity.CandidateProfile;
import owoke.staffmatch.backend.common.JsonSupport;

@Repository
public class CandidateProfileRepository {
    private final JdbcClient jdbc;
    private final JsonSupport json;

    public CandidateProfileRepository(JdbcClient jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void save(UUID userId, CandidateProfileRequest profile) {
        jdbc.sql("""
                INSERT INTO candidate_profiles(user_id, city, work_formats, available_hours,
                    salary_expectation, experience_months, portfolio_url, skills)
                VALUES (:id, :city, CAST(:formats AS jsonb), :hours, :salary, :experience,
                    :portfolio, CAST(:skills AS jsonb))
                ON CONFLICT (user_id) DO UPDATE SET city = EXCLUDED.city,
                    work_formats = EXCLUDED.work_formats, available_hours = EXCLUDED.available_hours,
                    salary_expectation = EXCLUDED.salary_expectation,
                    experience_months = EXCLUDED.experience_months, portfolio_url = EXCLUDED.portfolio_url,
                    skills = EXCLUDED.skills, updated_at = CURRENT_TIMESTAMP
                """).param("id", userId).param("city", profile.city())
                .param("formats", json.write(profile.workFormats()))
                .param("hours", profile.availableHours())
                .param("salary", profile.salaryExpectation())
                .param("experience", profile.experienceMonths())
                .param("portfolio", profile.portfolioUrl())
                .param("skills", json.write(profile.skills())).update();
    }

    public Optional<CandidateProfile> find(UUID userId) {
        return jdbc.sql("SELECT * FROM candidate_profiles WHERE user_id = :id")
                .param("id", userId).query((rs, n) -> map(rs)).optional();
    }

    public List<CandidateProfile> allComplete() {
        return jdbc.sql("""
                SELECT p.* FROM candidate_profiles p JOIN users u ON u.id = p.user_id
                WHERE u.role = 'CANDIDATE' AND jsonb_array_length(p.skills) > 0
                    AND jsonb_array_length(p.work_formats) > 0
                ORDER BY p.user_id
                """).query((rs, n) -> map(rs)).list();
    }

    private static CandidateProfile map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CandidateProfile(rs.getObject("user_id", UUID.class), rs.getString("city"),
                rs.getString("work_formats"), (Integer) rs.getObject("available_hours"),
                (Long) rs.getObject("salary_expectation"), (Integer) rs.getObject("experience_months"),
                rs.getString("portfolio_url"), rs.getString("skills"));
    }

}
