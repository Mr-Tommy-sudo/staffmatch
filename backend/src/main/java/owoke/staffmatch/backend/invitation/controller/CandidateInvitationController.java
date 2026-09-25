package owoke.staffmatch.backend.invitation.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import owoke.staffmatch.backend.auth.model.MaxPrincipal;
import owoke.staffmatch.backend.common.AccessService;
import owoke.staffmatch.backend.invitation.entity.Invitation;
import owoke.staffmatch.backend.invitation.service.InvitationService;
import owoke.staffmatch.backend.user.entity.UserRole;

@RestController
@RequestMapping("/api/v1/candidate/invitations")
public class CandidateInvitationController {
    private final AccessService access;
    private final InvitationService invitations;

    public CandidateInvitationController(AccessService access, InvitationService invitations) {
        this.access = access; this.invitations = invitations;
    }

    @GetMapping
    public List<Invitation> list(@AuthenticationPrincipal MaxPrincipal principal) {
        return invitations.forCandidate(access.require(principal, UserRole.CANDIDATE));
    }

    @PutMapping("/{id}/decision")
    public Invitation decide(@AuthenticationPrincipal MaxPrincipal principal,
                                                  @PathVariable UUID id,
                                                  @Valid @RequestBody DecisionRequest request) {
        return invitations.decide(access.require(principal, UserRole.CANDIDATE), id, request.decision());
    }

    public record DecisionRequest(@NotBlank String decision) {}
}
