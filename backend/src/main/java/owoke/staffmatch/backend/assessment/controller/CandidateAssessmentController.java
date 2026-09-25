package owoke.staffmatch.backend.assessment.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import owoke.staffmatch.backend.assessment.dto.AnswerSubmission;
import owoke.staffmatch.backend.assessment.service.AssessmentService;
import owoke.staffmatch.backend.auth.model.MaxPrincipal;
import owoke.staffmatch.backend.common.AccessService;
import owoke.staffmatch.backend.user.entity.UserRole;

@RestController
@RequestMapping("/api/v1/candidate/test-assignments")
public class CandidateAssessmentController {
    private final AccessService access;
    private final AssessmentService assessments;

    public CandidateAssessmentController(AccessService access, AssessmentService assessments) {
        this.access = access; this.assessments = assessments;
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal MaxPrincipal principal) {
        return assessments.candidateList(access.require(principal, UserRole.CANDIDATE));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal MaxPrincipal principal, @PathVariable UUID id) {
        return assessments.candidateTest(access.require(principal, UserRole.CANDIDATE), id);
    }

    @PostMapping("/{id}/answers")
    public Map<String, Object> submit(@AuthenticationPrincipal MaxPrincipal principal,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody AnswerSubmission answers) {
        return assessments.submit(access.require(principal, UserRole.CANDIDATE), id, answers);
    }
}
