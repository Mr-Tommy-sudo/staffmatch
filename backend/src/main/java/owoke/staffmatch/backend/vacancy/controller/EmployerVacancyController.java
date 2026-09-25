package owoke.staffmatch.backend.vacancy.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import owoke.staffmatch.backend.assessment.service.AssessmentService;
import owoke.staffmatch.backend.assessment.service.TestDefinitionService;
import owoke.staffmatch.backend.auth.model.MaxPrincipal;
import owoke.staffmatch.backend.common.AccessService;
import owoke.staffmatch.backend.invitation.service.InvitationService;
import owoke.staffmatch.backend.matching.service.MatchingService;
import owoke.staffmatch.backend.ranking.service.RankingService;
import owoke.staffmatch.backend.user.entity.UserRole;
import owoke.staffmatch.backend.vacancy.dto.VacancyCreateRequest;
import owoke.staffmatch.backend.vacancy.service.VacancyService;
import owoke.staffmatch.backend.vacancy.service.VacancyWorkflowService;

@RestController
@RequestMapping("/api/v1/employer/vacancies")
public class EmployerVacancyController {
    private final AccessService access;
    private final VacancyService vacancies;
    private final VacancyWorkflowService workflow;
    private final MatchingService matching;
    private final RankingService ranking;
    private final AssessmentService assessments;
    private final TestDefinitionService tests;
    private final InvitationService invitations;

    public EmployerVacancyController(AccessService access, VacancyService vacancies,
                                     VacancyWorkflowService workflow, MatchingService matching,
                                     RankingService ranking, AssessmentService assessments,
                                     TestDefinitionService tests,
                                     InvitationService invitations) {
        this.access = access; this.vacancies = vacancies; this.workflow = workflow;
        this.matching = matching; this.ranking = ranking;
        this.assessments = assessments; this.tests = tests; this.invitations = invitations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal MaxPrincipal principal,
                                      @Valid @RequestBody VacancyCreateRequest request) {
        UUID employer = access.require(principal, UserRole.EMPLOYER);
        UUID id = vacancies.create(employer, request);
        workflow.initialize(id, request);
        return vacancies.get(employer, id);
    }

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal MaxPrincipal principal) {
        return vacancies.list(access.require(principal, UserRole.EMPLOYER));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@AuthenticationPrincipal MaxPrincipal principal, @PathVariable UUID id) {
        return vacancies.get(access.require(principal, UserRole.EMPLOYER), id);
    }

    @GetMapping("/{id}/matches")
    public List<Map<String, Object>> matches(@AuthenticationPrincipal MaxPrincipal principal,
                                              @PathVariable UUID id) {
        vacancies.owned(access.require(principal, UserRole.EMPLOYER), id);
        return matching.list(id).stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("candidateId", m.candidateId()); item.put("eligible", m.eligible());
            item.put("score", m.score()); item.put("matched", m.matched());
            item.put("partial", m.partial()); item.put("missing", m.missing());
            item.put("algorithmVersion", m.algorithmVersion());
            return item;
        }).toList();
    }

    @PostMapping("/{id}/matching/recalculate")
    public Map<String, Object> recalculate(@AuthenticationPrincipal MaxPrincipal principal,
                                            @PathVariable UUID id) {
        UUID employer = access.require(principal, UserRole.EMPLOYER);
        vacancies.owned(employer, id);
        workflow.recalculateMatching(id);
        return vacancies.get(employer, id);
    }

    @PostMapping("/{id}/test/generate/retry")
    public Map<String, Object> retryGeneration(@AuthenticationPrincipal MaxPrincipal principal,
                                                @PathVariable UUID id) {
        UUID employer = access.require(principal, UserRole.EMPLOYER);
        workflow.generate(vacancies.owned(employer, id));
        workflow.recalculateRanking(id);
        return vacancies.get(employer, id);
    }

    @GetMapping("/{id}/ranking")
    public Map<String, Object> ranking(@AuthenticationPrincipal MaxPrincipal principal,
                                       @PathVariable UUID id) {
        vacancies.owned(access.require(principal, UserRole.EMPLOYER), id);
        return ranking.view(id);
    }

    @GetMapping("/{id}/test")
    public Map<String, Object> test(@AuthenticationPrincipal MaxPrincipal principal,
                                     @PathVariable UUID id) {
        vacancies.owned(access.require(principal, UserRole.EMPLOYER), id);
        return tests.employerTest(id);
    }

    @PostMapping("/{id}/assignments")
    public Map<String, Object> assign(@AuthenticationPrincipal MaxPrincipal principal,
                                      @PathVariable UUID id, @Valid @RequestBody CandidateRef request) {
        vacancies.owned(access.require(principal, UserRole.EMPLOYER), id);
        return assessments.assign(id, request.candidateId());
    }

    @GetMapping("/{id}/results")
    public List<Map<String, Object>> results(@AuthenticationPrincipal MaxPrincipal principal,
                                              @PathVariable UUID id) {
        vacancies.owned(access.require(principal, UserRole.EMPLOYER), id);
        return assessments.employerResults(id);
    }

    @PostMapping("/{id}/assignments/{assignmentId}/score/retry")
    public List<Map<String, Object>> retryScore(@AuthenticationPrincipal MaxPrincipal principal,
                                                 @PathVariable UUID id, @PathVariable UUID assignmentId) {
        vacancies.owned(access.require(principal, UserRole.EMPLOYER), id);
        assessments.requiredAssignment(id, assignmentId);
        assessments.retryScore(assignmentId);
        return assessments.employerResults(id);
    }

    @PostMapping("/{id}/invitations")
    public Object invite(@AuthenticationPrincipal MaxPrincipal principal,
                         @PathVariable UUID id, @Valid @RequestBody CandidateRef request) {
        vacancies.owned(access.require(principal, UserRole.EMPLOYER), id);
        return invitations.create(id, request.candidateId());
    }

    public record CandidateRef(@NotNull UUID candidateId) {}
}
