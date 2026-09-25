package owoke.staffmatch.backend.candidate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import owoke.staffmatch.backend.common.WorkFormat;

public record CandidateProfileRequest(
        String city,
        @NotEmpty List<WorkFormat> workFormats,
        @Min(0) @Max(168) Integer availableHours,
        @Min(0) Long salaryExpectation,
        @Min(0) Integer experienceMonths,
        String portfolioUrl,
        @NotEmpty List<@Valid Skill> skills
) {
    public record Skill(@NotBlank String code, @Min(1) @Max(5) int level) {}
}
