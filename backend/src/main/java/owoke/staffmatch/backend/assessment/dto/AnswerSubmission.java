package owoke.staffmatch.backend.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AnswerSubmission(@NotEmpty List<@Valid Answer> answers) {
    public record Answer(@NotBlank String questionId, Integer selectedOptionIndex, String text) {}
}
