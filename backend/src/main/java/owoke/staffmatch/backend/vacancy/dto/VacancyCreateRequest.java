package owoke.staffmatch.backend.vacancy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import owoke.staffmatch.backend.common.WorkFormat;

public record VacancyCreateRequest(
        @NotBlank String role,
        String city,
        @NotNull WorkFormat workFormat,
        @Min(0) @Max(168) Integer hoursMin,
        @Min(0) Long salaryFrom,
        @Min(0) Long salaryTo,
        @Min(0) Integer experienceMonthsMin,
        @NotEmpty List<@Valid Skill> skills,
        @NotNull TestMode testMode,
        String level,
        Integer durationMinutes,
        Integer questionCount,
        List<@Valid Competency> competencies,
        List<@Valid CustomQuestion> customQuestions
) {
    public enum TestMode { NONE, AUTO, CUSTOM }
    public record Skill(@NotBlank String code, @Min(1) @Max(5) int minLevel,
                        boolean required, @Min(0) int weight) {}
    public record Competency(@NotBlank String code, @Min(0) int weight) {}
    public record CustomQuestion(@NotBlank String type, @NotBlank String competency,
                                 @NotBlank String text, List<String> options,
                                 Object correctAnswer, Object rubric, @Min(1) Integer maxScore) {}
}
