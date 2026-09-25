package owoke.staffmatch.backend.matching.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import owoke.staffmatch.backend.candidate.repository.CandidateProfileRepository;
import owoke.staffmatch.backend.candidate.entity.CandidateProfile;
import owoke.staffmatch.backend.common.ApiException;
import owoke.staffmatch.backend.common.JsonSupport;
import owoke.staffmatch.backend.matching.repository.MatchingRepository;
import owoke.staffmatch.backend.matching.entity.MatchResult;
import owoke.staffmatch.backend.python.PythonGateway;
import owoke.staffmatch.backend.python.PythonOperation;
import owoke.staffmatch.backend.vacancy.repository.VacancyRepository;
import owoke.staffmatch.backend.vacancy.entity.Vacancy;
import tools.jackson.databind.JsonNode;

@Service
public class MatchingService {
    private final CandidateProfileRepository profiles;
    private final MatchingRepository matches;
    private final PythonGateway python;
    private final JsonSupport json;

    public MatchingService(CandidateProfileRepository profiles, MatchingRepository matches,
                           PythonGateway python, JsonSupport json) {
        this.profiles = profiles; this.matches = matches; this.python = python; this.json = json;
    }

    public void calculate(Vacancy vacancy) {
        List<CandidateProfile> candidates = profiles.allComplete();
        Map<String, Object> vacancyInput = vacancyInput(vacancy);
        List<MatchResult> calculated = new ArrayList<>();
        for (int start = 0; start < candidates.size(); start += 100) {
            var batch = candidates.subList(start, Math.min(start + 100, candidates.size()));
            List<Map<String, Object>> candidateInputs = batch.stream().map(this::candidateInput).toList();
            Map<String, Object> request = Map.of("vacancy", vacancyInput, "candidates", candidateInputs);
            JsonNode response = python.call(PythonOperation.MATCHING, request,
                    "matching:" + vacancy.id() + ":" + start + ":" + UUID.randomUUID());
            if (!vacancy.id().toString().equals(json.requiredText(response, "vacancyId"))) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned another vacancyId");
            }
            String version = json.requiredText(response, "algorithmVersion");
            Set<String> expected = new HashSet<>();
            candidateInputs.forEach(c -> expected.add(c.get("candidateId").toString()));
            Set<String> received = new HashSet<>();
            for (JsonNode item : json.requiredArray(response, "matches")) {
                String candidateId = json.requiredText(item, "candidateId");
                if (!expected.contains(candidateId) || !received.add(candidateId) ||
                        !item.path("eligible").isBoolean()) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Python returned invalid match identity");
                }
                UUID id = UUID.fromString(candidateId);
                Map<String, Object> candidate = candidateInputs.stream()
                        .filter(c -> candidateId.equals(c.get("candidateId"))).findFirst().orElseThrow();
                calculated.add(new MatchResult(id, item.path("eligible").booleanValue(),
                        json.score(item, "score"), json.requiredArray(item, "matched"),
                        json.requiredArray(item, "partial"), json.requiredArray(item, "missing"),
                        version, json.tree(Map.of("vacancy", vacancyInput, "candidate", candidate))));
            }
            if (!received.equals(expected)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Python omitted matching candidates");
            }
        }
        matches.replace(vacancy.id(), calculated);
    }

    private Map<String, Object> vacancyInput(Vacancy v) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", v.id().toString()); result.put("role", v.role());
        result.put("city", v.city()); result.put("workFormat", v.workFormat());
        result.put("hoursMin", v.hoursMin()); result.put("salaryFrom", v.salaryFrom());
        result.put("salaryTo", v.salaryTo());
        result.put("experienceMonthsMin", v.experienceMonthsMin());
        result.put("skills", json.read(v.skills()));
        return result;
    }

    private Map<String, Object> candidateInput(CandidateProfile p) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateId", p.userId().toString()); result.put("city", p.city());
        result.put("workFormats", json.read(p.workFormats()));
        result.put("availableHours", p.availableHours());
        result.put("salaryExpectation", p.salaryExpectation());
        result.put("experienceMonths", p.experienceMonths());
        result.put("portfolioAvailable", p.portfolioUrl() != null && !p.portfolioUrl().isBlank());
        result.put("skills", json.read(p.skills()));
        return result;
    }

    public List<MatchResult> list(UUID vacancyId) {
        return matches.byVacancy(vacancyId);
    }
}
