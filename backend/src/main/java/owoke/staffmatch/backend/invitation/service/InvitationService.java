package owoke.staffmatch.backend.invitation.service;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import owoke.staffmatch.backend.common.ApiException;
import owoke.staffmatch.backend.invitation.entity.Invitation;
import owoke.staffmatch.backend.invitation.repository.InvitationRepository;
import owoke.staffmatch.backend.ranking.service.RankingService;
import owoke.staffmatch.backend.vacancy.repository.VacancyRepository;

@Service
public class InvitationService {
    private final InvitationRepository invitations;
    private final RankingService ranking;
    private final VacancyRepository vacancies;

    public InvitationService(InvitationRepository invitations, RankingService ranking,
                             VacancyRepository vacancies) {
        this.invitations = invitations; this.ranking = ranking; this.vacancies = vacancies;
    }

    public Invitation create(UUID vacancyId, UUID candidateId) {
        var vacancy = vacancies.find(vacancyId).orElseThrow();
        if (!vacancy.matchingStatus().equals("READY") || !vacancy.rankingStatus().equals("READY")) {
            throw new ApiException(HttpStatus.CONFLICT, "Ranking is not ready");
        }
        if (!ranking.isFinal(vacancyId, candidateId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Candidate is not in final ranking");
        }
        invitations.create(UUID.randomUUID(), vacancyId, candidateId);
        return invitations.find(vacancyId, candidateId).orElseThrow();
    }

    public List<Invitation> forCandidate(UUID candidateId) {
        return invitations.byCandidate(candidateId);
    }

    public Invitation decide(UUID candidateId, UUID invitationId, String decision) {
        if (!decision.equals("ACCEPTED") && !decision.equals("DECLINED")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Decision must be ACCEPTED or DECLINED");
        }
        var invitation = invitations.find(invitationId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Invitation not found"));
        if (!invitation.candidateId().equals(candidateId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Invitation not found");
        }
        if (!invitation.status().equals(decision) && invitations.decide(invitationId, decision) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Invitation already has another decision");
        }
        return invitations.find(invitationId).orElseThrow();
    }
}
