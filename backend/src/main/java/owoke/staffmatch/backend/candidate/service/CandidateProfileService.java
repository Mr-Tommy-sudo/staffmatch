package owoke.staffmatch.backend.candidate.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import owoke.staffmatch.backend.candidate.dto.CandidateProfileRequest;
import owoke.staffmatch.backend.candidate.repository.CandidateProfileRepository;
import owoke.staffmatch.backend.common.ApiException;
import owoke.staffmatch.backend.common.JsonSupport;

@Service
public class CandidateProfileService {
    private final CandidateProfileRepository profiles;
    private final JsonSupport json;

    public CandidateProfileService(CandidateProfileRepository profiles, JsonSupport json) {
        this.profiles = profiles;
        this.json = json;
    }

    public Map<String, Object> save(UUID userId, CandidateProfileRequest request) {
        var formats = new HashSet<>(request.workFormats());
        if (formats.size() != request.workFormats().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate work format");
        }
        var codes = new HashSet<String>();
        for (var skill : request.skills()) {
            if (!codes.add(skill.code())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate skill code");
            }
        }
        profiles.save(userId, request);
        return get(userId);
    }

    public Map<String, Object> get(UUID userId) {
        var p = profiles.find(userId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Candidate profile not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateId", p.userId());
        result.put("city", p.city());
        result.put("workFormats", json.read(p.workFormats()));
        result.put("availableHours", p.availableHours());
        result.put("salaryExpectation", p.salaryExpectation());
        result.put("experienceMonths", p.experienceMonths());
        result.put("portfolioUrl", p.portfolioUrl());
        result.put("portfolioAvailable", p.portfolioUrl() != null && !p.portfolioUrl().isBlank());
        result.put("skills", json.read(p.skills()));
        return result;
    }
}
