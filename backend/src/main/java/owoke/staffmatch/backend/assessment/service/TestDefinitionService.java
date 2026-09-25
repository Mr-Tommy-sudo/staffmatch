package owoke.staffmatch.backend.assessment.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import owoke.staffmatch.backend.assessment.repository.TestRepository;
import owoke.staffmatch.backend.assessment.entity.TestDefinition;
import owoke.staffmatch.backend.common.ApiException;
import owoke.staffmatch.backend.common.JsonSupport;
import owoke.staffmatch.backend.python.PythonGateway;
import owoke.staffmatch.backend.python.PythonOperation;
import owoke.staffmatch.backend.vacancy.dto.VacancyCreateRequest;
import owoke.staffmatch.backend.vacancy.repository.VacancyRepository;
import owoke.staffmatch.backend.vacancy.entity.Vacancy;
import tools.jackson.databind.JsonNode;

@Service
public class TestDefinitionService {
    private final TestRepository tests;
    private final PythonGateway python;
    private final JsonSupport json;
    private final TransactionTemplate transactions;

    public TestDefinitionService(TestRepository tests, PythonGateway python, JsonSupport json,
                                 TransactionTemplate transactions) {
        this.tests = tests; this.python = python; this.json = json; this.transactions = transactions;
    }

    public void generate(Vacancy v) {
        if (tests.findByVacancy(v.id()).isPresent()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vacancyId", v.id().toString()); body.put("mode", "AUTO");
        body.put("role", v.role()); body.put("level", v.level());
        body.put("durationMinutes", v.durationMinutes());
        body.put("questionCount", v.questionCount());
        body.put("competencies", json.read(v.competencies()));
        JsonNode result = python.call(PythonOperation.GENERATE, body, "testgen:" + v.id());
        String externalId = json.requiredText(result, "testId");
        String version = json.requiredText(result, "generationVersion");
        JsonNode duration = result.path("estimatedDurationMinutes");
        if (!duration.isIntegralNumber() || duration.intValue() < 5 || duration.intValue() > 10) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned invalid test duration");
        }
        save(v.id(), externalId, version, duration.intValue(), json.requiredArray(result, "questions"),
                json.tree(body));
    }

    public void createCustom(UUID vacancyId, VacancyCreateRequest request) {
        List<Map<String, Object>> questions = new ArrayList<>();
        int position = 0;
        for (var q : request.customQuestions()) {
            position++;
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("questionId", "q" + position); question.put("type", q.type());
            question.put("competency", q.competency()); question.put("text", q.text());
            question.put("options", q.options()); question.put("correctAnswer", q.correctAnswer());
            question.put("rubric", q.rubric()); question.put("maxScore", q.maxScore());
            questions.add(question);
        }
        save(vacancyId, null, "manual-v1",
                request.durationMinutes() == null ? 7 : request.durationMinutes(),
                json.tree(questions), json.tree(request));
    }

    public void save(UUID vacancyId, String externalId, String version, int duration,
                     JsonNode questions, JsonNode generationInput) {
        if (questions.isEmpty()) throw new ApiException(HttpStatus.BAD_GATEWAY, "Test has no questions");
        transactions.executeWithoutResult(status -> {
            if (tests.findByVacancy(vacancyId).isPresent()) return;
            UUID testId = UUID.randomUUID();
            if (tests.insert(new TestDefinition(testId, vacancyId, externalId, version, duration,
                    json.write(generationInput))) == 0) {
                return;
            }
            int position = 0;
            for (JsonNode q : questions) {
                position++;
                String id = json.requiredText(q, "questionId");
                String type = json.requiredText(q, "type");
                json.requiredText(q, "text");
                json.requiredText(q, "competency");
                if (!type.equals("SINGLE_CHOICE") && !type.equals("FREE_TEXT")) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Unsupported question type");
                }
                JsonNode maxScore = q.path("maxScore").isIntegralNumber()
                        ? q.path("maxScore") : q.path("rubric").path("maxScore");
                if (!maxScore.isIntegralNumber() || maxScore.intValue() <= 0) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Question needs maxScore");
                }
                if (type.equals("SINGLE_CHOICE") &&
                        (!q.path("options").isArray() || !q.path("correctAnswer").path("optionIndex").isIntegralNumber())) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Choice question is incomplete");
                }
                if (type.equals("FREE_TEXT") && !q.path("rubric").isObject()) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Free-text question needs rubric");
                }
                tests.insertQuestion(testId, id, position, json.write(q));
            }
        });
    }

    public TestDefinition required(UUID vacancyId) {
        return tests.findByVacancy(vacancyId).orElseThrow(
                () -> new ApiException(HttpStatus.CONFLICT, "Vacancy test is not ready"));
    }

    public Map<String, Object> employerTest(UUID vacancyId) {
        var test = required(vacancyId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", test.id());
        view.put("vacancyId", vacancyId);
        view.put("generationVersion", test.generationVersion());
        view.put("estimatedDurationMinutes", test.estimatedDurationMinutes());
        view.put("questions", tests.questions(test.id()).stream()
                .map(row -> json.read(row.json())).toList());
        return view;
    }

    public List<Map<String, Object>> candidateQuestions(UUID vacancyId) {
        var test = required(vacancyId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : tests.questions(test.id())) {
            JsonNode q = json.read(row.json());
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("questionId", row.questionId()); view.put("position", row.position());
            view.put("type", q.path("type").asText());
            view.put("text", q.path("text").asText());
            view.put("options", q.path("options").isArray() ? q.path("options") : List.of());
            result.add(view);
        }
        return result;
    }

    public List<Map<String, Object>> scoringQuestions(UUID vacancyId) {
        var test = required(vacancyId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : tests.questions(test.id())) {
            JsonNode q = json.read(row.json());
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("questionId", row.questionId()); view.put("type", q.path("type").asText());
            view.put("competency", q.path("competency").asText());
            JsonNode maxScore = q.path("maxScore").isIntegralNumber()
                    ? q.path("maxScore") : q.path("rubric").path("maxScore");
            view.put("maxScore", maxScore.intValue());
            if (q.path("type").asText().equals("SINGLE_CHOICE")) {
                view.put("correctAnswer", q.path("correctAnswer"));
            } else {
                view.put("rubric", q.path("rubric"));
            }
            result.add(view);
        }
        return result;
    }
}
