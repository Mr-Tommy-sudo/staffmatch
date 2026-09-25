package owoke.staffmatch.backend.vacancy.entity;

import java.util.UUID;

public record Vacancy(UUID id, UUID employerId, String role, String city, String workFormat,
                      Integer hoursMin, Long salaryFrom, Long salaryTo, Integer experienceMonthsMin,
                      String skills, String testMode, String level, Integer durationMinutes,
                      Integer questionCount, String competencies, String matchingStatus,
                      String generationStatus, String rankingStatus, String lastError) {}
