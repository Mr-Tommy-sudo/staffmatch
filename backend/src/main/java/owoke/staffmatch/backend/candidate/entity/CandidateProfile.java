package owoke.staffmatch.backend.candidate.entity;

import java.util.UUID;

public record CandidateProfile(UUID userId, String city, String workFormats,
                               Integer availableHours, Long salaryExpectation,
                               Integer experienceMonths, String portfolioUrl, String skills) {}
