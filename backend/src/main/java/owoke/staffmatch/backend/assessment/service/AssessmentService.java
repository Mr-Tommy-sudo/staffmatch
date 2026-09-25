package owoke.staffmatch.backend.assessment.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import owoke.staffmatch.backend.assessment.dto.AnswerSubmission;
import owoke.staffmatch.backend.assessment.entity.TestAssignment;
import owoke.staffmatch.backend.assessment.repository.AssessmentResultRepository;
import owoke.staffmatch.backend.assessment.repository.AssignmentRepository;
import owoke.staffmatch.backend.common.ApiException;
import owoke.staffmatch.backend.common.JsonSupport;
import owoke.staffmatch.backend.matching.repository.MatchingRepository;
import owoke.staffmatch.backend.python.PythonGateway;
import owoke.staffmatch.backend.python.PythonOperation;
import owoke.staffmatch.backend.vacancy.service.VacancyWorkflowService;
import owoke.staffmatch.backend.vacancy.repository.VacancyRepository;
import tools.jackson.databind.JsonNode;

@Service
public class AssessmentService {
    private final AssignmentRepository assignments;
    private final AssessmentResultRepository results;
    private final MatchingRepository matches;
    private final TestDefinitionService tests;
    private final PythonGateway python;
    private final JsonSupport json;
    private final VacancyWorkflowService workflow;
    private final VacancyRepository vacancies;

    public AssessmentService(AssignmentRepository assignments, AssessmentResultRepository results,
                             MatchingRepository matches, TestDefinitionService tests,
                             PythonGateway python, JsonSupport json, VacancyWorkflowService workflow,
                             VacancyRepository vacancies) {
        this.assignments = assignments; this.results = results; this.matches = matches;
        this.tests = tests; this.python = python; this.json = json; this.workflow = workflow;
        this.vacancies = vacancies;
    }

    public Map<String, Object> assign(UUID vacancyId, UUID candidateId) {
        var vacancy = vacancies.find(vacancyId).orElseThrow();
        if (!vacancy.matchingStatus().equals("READY") ||
                !vacancy.generationStatus().equals("READY")) {
            throw new ApiException(HttpStatus.CONFLICT, "Vacancy matching or test is not ready");
        }
        boolean eligible = matches.byVacancy(vacancyId).stream().anyMatch(m ->
                m.candidateId().equals(candidateId) && m.eligible());
        if (!eligible) throw new ApiException(HttpStatus.CONFLICT, "Candidate is not eligible for this vacancy");
        tests.required(vacancyId);
        assignments.create(UUID.randomUUID(), vacancyId, candidateId);
        return view(assignments.find(vacancyId, candidateId).orElseThrow(), false);
    }

    public List<Map<String, Object>> candidateList(UUID candidateId) {
        return assignments.byCandidate(candidateId).stream().map(a -> view(a, false)).toList();
    }

    public Map<String, Object> candidateTest(UUID candidateId, UUID assignmentId) {
        var assignment = candidateAssignment(candidateId, assignmentId);
        return view(assignment, true);
    }

    public Map<String, Object> submit(UUID candidateId, UUID assignmentId, AnswerSubmission submission) {
        var assignment = candidateAssignment(candidateId, assignmentId);
        validateAnswers(assignment.vacancyId(), submission.answers());
        List<Map<String, Object>> answers = submission.answers().stream().map(answer -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", answer.questionId());
            if (answer.selectedOptionIndex() != null) {
                item.put("selectedOptionIndex", answer.selectedOptionIndex());
            } else {
                item.put("text", answer.text());
            }
            return item;
        }).toList();
        String answersJson = json.write(answers);
        if (!assignment.status().equals("ASSIGNED")) {
            if (!json.read(answersJson).equals(json.read(assignment.answers()))) {
                throw new ApiException(HttpStatus.CONFLICT, "Answers were already submitted");
            }
        } else {
            Map<String, Object> scoreInput = Map.of("assignmentId", assignmentId.toString(),
                    "questions", tests.scoringQuestions(assignment.vacancyId()),
                    "answers", answers);
            if (assignments.submit(assignmentId, answersJson, json.write(scoreInput)) == 0) {
                var current = assignments.find(assignmentId).orElseThrow();
                if (!json.read(answersJson).equals(json.read(current.answers()))) {
                    throw new ApiException(HttpStatus.CONFLICT, "Answers were already submitted");
                }
            }
        }
        score(assignmentId);
        var updated = assignments.find(assignmentId).orElseThrow();
        Map<String, Object> response = view(updated, false);
        response.put("submitted", true);
        return response;
    }

    public void retryScore(UUID assignmentId) {
        var assignment = assignments.find(assignmentId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (assignment.answers() == null) throw new ApiException(HttpStatus.CONFLICT, "Answers not submitted");
        score(assignmentId);
    }

    private void score(UUID assignmentId) {
        var assignment = assignments.find(assignmentId).orElseThrow();
        if (results.find(assignmentId).isPresent()) return;
        try {
            JsonNode input = json.read(assignment.scoringInput());
            JsonNode response = python.call(PythonOperation.SCORE, input, "score:" + assignmentId);
            if (!assignmentId.toString().equals(json.requiredText(response, "assignmentId"))) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned another assignmentId");
            }
            double totalScore = json.score(response, "totalScore");
            String version = json.requiredText(response, "scoringVersion");
            JsonNode questionResults = json.requiredArray(response, "questions");
            JsonNode breakdown = json.requiredArray(response, "breakdown");
            Set<String> expected = new HashSet<>();
            Map<String, Double> expectedMaximums = new HashMap<>();
            for (JsonNode question : input.path("questions")) {
                String id = question.path("questionId").asText();
                expected.add(id);
                expectedMaximums.put(id, question.path("maxScore").doubleValue());
            }
            Set<String> received = new HashSet<>();
            for (JsonNode item : questionResults) {
                String id = json.requiredText(item, "questionId");
                if (!expected.contains(id) || !received.add(id) || !item.path("score").isNumber()
                        || !item.path("maxScore").isNumber()
                        || item.path("maxScore").doubleValue() != expectedMaximums.get(id)
                        || item.path("score").doubleValue() < 0
                        || item.path("score").doubleValue() > item.path("maxScore").doubleValue()) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned invalid question score");
                }
            }
            if (!received.equals(expected)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Python omitted question results");
            }
            for (JsonNode item : breakdown) {
                if (!item.path("score").isNumber() || !item.path("maxScore").isNumber()
                        || !item.path("percent").isNumber()
                        || item.path("score").doubleValue() < 0
                        || item.path("score").doubleValue() > item.path("maxScore").doubleValue()
                        || item.path("percent").doubleValue() < 0
                        || item.path("percent").doubleValue() > 100) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned invalid breakdown");
                }
            }
            results.save(assignmentId, totalScore, questionResults, breakdown,
                    response.path("summary").isTextual() ? response.path("summary").asText() : null,
                    version, response.path("modelVersion").isTextual()
                            ? response.path("modelVersion").asText() : null);
            workflow.recalculateRanking(assignment.vacancyId());
        } catch (RuntimeException exception) {
            if (results.find(assignmentId).isEmpty()) {
                assignments.status(assignmentId, "SCORING_FAILED", exception.getMessage());
            }
        }
    }

    public List<Map<String, Object>> employerResults(UUID vacancyId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var assignment : assignments.byVacancy(vacancyId)) {
            Map<String, Object> row = view(assignment, false);
            results.find(assignment.id()).ifPresent(result -> row.put("result", result));
            rows.add(row);
        }
        return rows;
    }

    public TestAssignment requiredAssignment(UUID vacancyId, UUID assignmentId) {
        var assignment = assignments.find(assignmentId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (!assignment.vacancyId().equals(vacancyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignment not found");
        }
        return assignment;
    }

    private TestAssignment candidateAssignment(UUID candidateId, UUID assignmentId) {
        var assignment = assignments.find(assignmentId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (!assignment.candidateId().equals(candidateId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignment not found");
        }
        return assignment;
    }

    private Map<String, Object> view(TestAssignment a, boolean includeQuestions) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", a.id()); result.put("vacancyId", a.vacancyId());
        result.put("candidateId", a.candidateId()); result.put("status", a.status());
        result.put("submitted", a.answers() != null);
        if (includeQuestions) result.put("questions", tests.candidateQuestions(a.vacancyId()));
        return result;
    }

    private void validateAnswers(UUID vacancyId, List<AnswerSubmission.Answer> answers) {
        Map<String, Map<String, Object>> questions = new HashMap<>();
        for (var q : tests.candidateQuestions(vacancyId)) questions.put(q.get("questionId").toString(), q);
        Set<String> seen = new HashSet<>();
        for (var answer : answers) {
            var question = questions.get(answer.questionId());
            if (question == null || !seen.add(answer.questionId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown or duplicate question answer");
            }
            if (question.get("type").equals("SINGLE_CHOICE")) {
                JsonNode options = (JsonNode) question.get("options");
                if (answer.selectedOptionIndex() == null || answer.selectedOptionIndex() < 0
                        || answer.selectedOptionIndex() >= options.size() || answer.text() != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid choice answer");
                }
            } else if (answer.text() == null || answer.text().isBlank()
                    || answer.selectedOptionIndex() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid free-text answer");
            }
        }
        if (seen.size() != questions.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "All questions must be answered");
        }
    }
}
