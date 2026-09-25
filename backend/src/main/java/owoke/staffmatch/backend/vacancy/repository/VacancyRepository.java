package owoke.staffmatch.backend.vacancy.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import owoke.staffmatch.backend.common.JsonSupport;
import owoke.staffmatch.backend.vacancy.dto.VacancyCreateRequest;
import owoke.staffmatch.backend.vacancy.entity.Vacancy;

@Repository
public class VacancyRepository {
    private final JdbcClient jdbc;
    private final JsonSupport json;

    public VacancyRepository(JdbcClient jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void create(UUID id, UUID employerId, VacancyCreateRequest v) {
        Integer duration = v.testMode() == VacancyCreateRequest.TestMode.NONE ? null :
                v.durationMinutes() == null ? 7 : v.durationMinutes();
        Integer count = v.testMode() == VacancyCreateRequest.TestMode.AUTO ?
                v.questionCount() == null ? 7 : v.questionCount() : null;
        var competencies = v.competencies() == null || v.competencies().isEmpty()
                ? v.skills().stream().map(skill -> java.util.Map.of(
                        "code", skill.code(), "weight", skill.weight())).toList()
                : v.competencies();
        jdbc.sql("""
                INSERT INTO vacancies(id, employer_id, role, city, work_format, hours_min,
                    salary_from, salary_to, experience_months_min, skills, test_mode, level,
                    duration_minutes, question_count, competencies, generation_status)
                VALUES (:id, :employer, :role, :city, :format, :hours, :salaryFrom,
                    :salaryTo, :experience, CAST(:skills AS jsonb), :mode, :level,
                    :duration, :count, CAST(:competencies AS jsonb), :generation)
                """).param("id", id).param("employer", employerId)
                .param("role", v.role()).param("city", v.city())
                .param("format", v.workFormat().name()).param("hours", v.hoursMin())
                .param("salaryFrom", v.salaryFrom()).param("salaryTo", v.salaryTo())
                .param("experience", v.experienceMonthsMin())
                .param("skills", json.write(v.skills()))
                .param("mode", v.testMode().name())
                .param("level", v.level() == null ? "JUNIOR" : v.level())
                .param("duration", duration).param("count", count)
                .param("competencies", json.write(competencies))
                .param("generation", v.testMode() == VacancyCreateRequest.TestMode.AUTO
                        ? "PENDING" : "NOT_REQUIRED").update();
    }

    public Optional<Vacancy> find(UUID id) {
        return jdbc.sql("SELECT * FROM vacancies WHERE id = :id").param("id", id)
                .query((rs, n) -> new Vacancy(
                        rs.getObject("id", UUID.class), rs.getObject("employer_id", UUID.class),
                        rs.getString("role"), rs.getString("city"), rs.getString("work_format"),
                        (Integer) rs.getObject("hours_min"), (Long) rs.getObject("salary_from"),
                        (Long) rs.getObject("salary_to"), (Integer) rs.getObject("experience_months_min"),
                        rs.getString("skills"), rs.getString("test_mode"), rs.getString("level"),
                        (Integer) rs.getObject("duration_minutes"), (Integer) rs.getObject("question_count"),
                        rs.getString("competencies"), rs.getString("matching_status"),
                        rs.getString("generation_status"), rs.getString("ranking_status"),
                        rs.getString("last_error"))).optional();
    }

    public List<Vacancy> byEmployer(UUID employer) {
        List<UUID> ids = jdbc.sql("SELECT id FROM vacancies WHERE employer_id = :employer ORDER BY created_at DESC")
                .param("employer", employer).query((rs, n) -> rs.getObject(1, UUID.class)).list();
        return ids.stream().map(id -> find(id).orElseThrow()).toList();
    }

    public void matchingStatus(UUID id, String status, String error) {
        jdbc.sql("UPDATE vacancies SET matching_status = :status, last_error = :error WHERE id = :id")
                .param("status", status).param("error", error).param("id", id).update();
    }
    public void generationStatus(UUID id, String status, String error) {
        jdbc.sql("UPDATE vacancies SET generation_status = :status, last_error = :error WHERE id = :id")
                .param("status", status).param("error", error).param("id", id).update();
    }
    public void rankingStatus(UUID id, String status, String error) {
        jdbc.sql("UPDATE vacancies SET ranking_status = :status, last_error = :error WHERE id = :id")
                .param("status", status).param("error", error).param("id", id).update();
    }

}
