package owoke.staffmatch.backend.vacancy.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import owoke.staffmatch.backend.common.ApiException;
import owoke.staffmatch.backend.common.JsonSupport;
import owoke.staffmatch.backend.vacancy.dto.VacancyCreateRequest;
import owoke.staffmatch.backend.vacancy.repository.VacancyRepository;
import owoke.staffmatch.backend.vacancy.entity.Vacancy;

@Service
public class VacancyService {
    private final VacancyRepository vacancies;
    private final JsonSupport json;

    public VacancyService(VacancyRepository vacancies, JsonSupport json) {
        this.vacancies = vacancies;
        this.json = json;
    }

    @Transactional
    public UUID create(UUID employerId, VacancyCreateRequest request) {
        validate(request);
        UUID id = UUID.randomUUID();
        vacancies.create(id, employerId, request);
        return id;
    }

    public Vacancy owned(UUID employerId, UUID vacancyId) {
        var vacancy = vacancies.find(vacancyId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Vacancy not found"));
        if (!vacancy.employerId().equals(employerId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Vacancy not found");
        }
        return vacancy;
    }

    public Map<String, Object> get(UUID employerId, UUID vacancyId) {
        return view(owned(employerId, vacancyId));
    }

    public List<Map<String, Object>> list(UUID employerId) {
        return vacancies.byEmployer(employerId).stream().map(this::view).toList();
    }

    private Map<String, Object> view(Vacancy v) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", v.id()); result.put("role", v.role()); result.put("city", v.city());
        result.put("workFormat", v.workFormat()); result.put("hoursMin", v.hoursMin());
        result.put("salaryFrom", v.salaryFrom()); result.put("salaryTo", v.salaryTo());
        result.put("experienceMonthsMin", v.experienceMonthsMin());
        result.put("skills", json.read(v.skills())); result.put("testMode", v.testMode());
        result.put("matchingStatus", v.matchingStatus());
        result.put("generationStatus", v.generationStatus());
        result.put("rankingStatus", v.rankingStatus());
        result.put("generationError", v.generationError());
        result.put("matchingError", v.matchingError());
        result.put("rankingError", v.rankingError());
        result.put("lastError", java.util.stream.Stream.of(
                v.generationError(), v.matchingError(), v.rankingError())
                .filter(error -> error != null && !error.isBlank())
                .collect(java.util.stream.Collectors.joining("; ")));
        if ("".equals(result.get("lastError"))) result.put("lastError", null);
        return result;
    }

    private void validate(VacancyCreateRequest v) {
        var skillCodes = new HashSet<String>();
        for (var skill : v.skills()) {
            if (!skillCodes.add(skill.code())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate vacancy skill code");
            }
        }
        if (v.salaryFrom() != null && v.salaryTo() != null && v.salaryFrom() > v.salaryTo()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "salaryFrom must not exceed salaryTo");
        }
        if (v.testMode() == VacancyCreateRequest.TestMode.NONE) return;
        if (v.durationMinutes() != null && (v.durationMinutes() < 5 || v.durationMinutes() > 10)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Test duration must be 5–10 minutes");
        }
        if (v.testMode() == VacancyCreateRequest.TestMode.AUTO &&
                v.questionCount() != null && (v.questionCount() < 6 || v.questionCount() > 8)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTO test needs 6–8 questions");
        }
        if (v.testMode() == VacancyCreateRequest.TestMode.AUTO && v.competencies() != null) {
            var competencyCodes = new HashSet<String>();
            for (var competency : v.competencies()) {
                if (!competencyCodes.add(competency.code())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate competency code");
                }
            }
        }
        if (v.testMode() == VacancyCreateRequest.TestMode.CUSTOM &&
                (v.customQuestions() == null || v.customQuestions().isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM test needs questions");
        }
        if (v.testMode() == VacancyCreateRequest.TestMode.CUSTOM) {
            int totalMaxScore = 0;
            for (var question : v.customQuestions()) {
                if (question.maxScore() == null || question.maxScore() <= 0
                        || question.maxScore() > 100 || (totalMaxScore += question.maxScore()) > 100) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Test maxScore must be at most 100");
                }
                if (question.type().equals("SINGLE_CHOICE")) {
                    if (question.options() == null || question.options().size() < 2
                            || question.correctAnswer() == null
                            || !json.tree(question.correctAnswer()).path("optionIndex").isIntegralNumber()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Choice question is incomplete");
                    }
                    int index = json.tree(question.correctAnswer()).path("optionIndex").intValue();
                    if (index < 0 || index >= question.options().size()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Correct optionIndex is out of range");
                    }
                } else if (question.type().equals("FREE_TEXT")) {
                    var rubric = json.tree(question.rubric());
                    if (!rubric.isObject() || !rubric.path("criteria").isArray()
                            || rubric.path("criteria").isEmpty()) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Free-text question needs valid rubric criteria");
                    }
                    for (var criterion : rubric.path("criteria")) {
                        if (!criterion.isTextual() || criterion.asText().isBlank()) {
                            throw new ApiException(HttpStatus.BAD_REQUEST, "Free-text question needs valid rubric criteria");
                        }
                    }
                    var rubricMaxScore = rubric.path("maxScore");
                    if (!rubricMaxScore.isMissingNode()
                            && (!rubricMaxScore.isIntegralNumber()
                                    || rubricMaxScore.intValue() != question.maxScore())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Rubric maxScore must match question maxScore");
                    }
                } else {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported question type");
                }
            }
        }
    }
}
