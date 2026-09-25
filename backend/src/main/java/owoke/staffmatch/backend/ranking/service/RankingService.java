package owoke.staffmatch.backend.ranking.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import owoke.staffmatch.backend.assessment.repository.AssessmentResultRepository;
import owoke.staffmatch.backend.assessment.entity.AssessmentResult;
import owoke.staffmatch.backend.assessment.repository.AssignmentRepository;
import owoke.staffmatch.backend.common.ApiException;
import owoke.staffmatch.backend.common.JsonSupport;
import owoke.staffmatch.backend.matching.repository.MatchingRepository;
import owoke.staffmatch.backend.python.PythonGateway;
import owoke.staffmatch.backend.python.PythonOperation;
import owoke.staffmatch.backend.ranking.repository.RankingRepository;
import owoke.staffmatch.backend.ranking.entity.RankingEntry;
import owoke.staffmatch.backend.vacancy.repository.VacancyRepository;
import owoke.staffmatch.backend.vacancy.entity.Vacancy;
import tools.jackson.databind.JsonNode;

@Service
public class RankingService {
    private final MatchingRepository matches;
    private final AssignmentRepository assignments;
    private final AssessmentResultRepository results;
    private final RankingRepository ranking;
    private final PythonGateway python;
    private final JsonSupport json;

    public RankingService(MatchingRepository matches, AssignmentRepository assignments,
                          AssessmentResultRepository results, RankingRepository ranking,
                          PythonGateway python, JsonSupport json) {
        this.matches = matches; this.assignments = assignments; this.results = results;
        this.ranking = ranking; this.python = python; this.json = json;
    }

    public void calculate(Vacancy vacancy) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (var match : matches.byVacancy(vacancy.id())) {
            if (!match.eligible()) continue;
            var assignment = assignments.find(vacancy.id(), match.candidateId());
            var result = assignment.flatMap(a -> results.find(a.id()));
            String state = vacancy.testMode().equals("NONE") ? "NOT_REQUIRED" :
                    result.isPresent() ? "COMPLETED" : "WAITING";
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("candidateId", match.candidateId().toString());
            item.put("matchingScore", match.score());
            item.put("testScore", result.map(AssessmentResult::totalScore).orElse(null));
            item.put("testState", state);
            candidates.add(item);
        }
        if (candidates.isEmpty()) {
            ranking.replace(vacancy.id(), List.of());
            return;
        }
        Map<String, Object> request = Map.of("vacancyId", vacancy.id().toString(),
                "weights", Map.of("matching", 0.6, "assessment", 0.4), "candidates", candidates);
        JsonNode response = python.call(PythonOperation.RANK, request,
                "ranking:" + vacancy.id() + ":" + UUID.randomUUID());
        if (!vacancy.id().toString().equals(json.requiredText(response, "vacancyId"))) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned another vacancyId");
        }
        String version = json.requiredText(response, "rankingVersion");
        Set<String> expected = new HashSet<>();
        candidates.forEach(c -> expected.add(c.get("candidateId").toString()));
        Set<String> received = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        List<RankingEntry> entries = new ArrayList<>();
        for (JsonNode item : json.requiredArray(response, "final")) {
            String id = json.requiredText(item, "candidateId");
            if (!expected.contains(id) || !received.add(id) || !item.path("rank").isIntegralNumber()
                    || item.path("rank").intValue() < 1
                    || !ranks.add(item.path("rank").intValue())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned invalid ranking identity");
            }
            Map<String, Object> candidate = candidates.stream()
                    .filter(c -> id.equals(c.get("candidateId"))).findFirst().orElseThrow();
            if (candidate.get("testState").equals("WAITING")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Waiting candidate cannot be final");
            }
            String expectedState = candidate.get("testState").equals("NOT_REQUIRED") ? "NO_TEST" : "FINAL";
            if (!expectedState.equals(json.requiredText(item, "state"))
                    || !item.path("components").isObject()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned invalid final state");
            }
            entries.add(new RankingEntry(UUID.fromString(id), "FINAL",
                    item.path("rank").intValue(), json.score(item, "finalScore"),
                    expectedState, item.path("components"),
                    item.path("explanation").isTextual() ? item.path("explanation").asText() : null,
                    version, json.tree(request)));
        }
        for (JsonNode item : json.requiredArray(response, "waiting")) {
            String id = json.requiredText(item, "candidateId");
            if (!expected.contains(id) || !received.add(id)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned invalid waiting identity");
            }
            Map<String, Object> candidate = candidates.stream()
                    .filter(c -> id.equals(c.get("candidateId"))).findFirst().orElseThrow();
            if (!candidate.get("testState").equals("WAITING")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Completed candidate cannot be waiting");
            }
            if (!json.requiredText(item, "state").equals("WAITING_TEST")
                    || !item.path("matchingScore").isNumber()
                    || item.path("matchingScore").doubleValue()
                            != ((Number) candidate.get("matchingScore")).doubleValue()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned invalid waiting state");
            }
            entries.add(new RankingEntry(UUID.fromString(id), "WAITING", null, null,
                    "WAITING_TEST", json.tree(Map.of("matching", candidate.get("matchingScore"))),
                    null, version, json.tree(request)));
        }
        if (!received.equals(expected)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Python omitted ranking candidates");
        }
        ranking.replace(vacancy.id(), entries);
    }

    public Map<String, Object> view(UUID vacancyId) {
        var entries = ranking.byVacancy(vacancyId);
        return Map.of("vacancyId", vacancyId,
                "final", entries.stream().filter(e -> e.bucket().equals("FINAL")).map(this::view).toList(),
                "waiting", entries.stream().filter(e -> e.bucket().equals("WAITING")).map(this::view).toList());
    }

    private Map<String, Object> view(RankingEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateId", entry.candidateId());
        result.put("rank", entry.rank()); result.put("finalScore", entry.finalScore());
        result.put("state", entry.state()); result.put("components", entry.components());
        result.put("explanation", entry.explanation());
        result.put("rankingVersion", entry.version());
        return result;
    }

    public boolean isFinal(UUID vacancyId, UUID candidateId) {
        return ranking.isFinal(vacancyId, candidateId);
    }
}
