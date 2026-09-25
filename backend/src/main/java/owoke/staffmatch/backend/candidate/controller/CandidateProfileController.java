package owoke.staffmatch.backend.candidate.controller;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import owoke.staffmatch.backend.auth.model.MaxPrincipal;
import owoke.staffmatch.backend.candidate.dto.CandidateProfileRequest;
import owoke.staffmatch.backend.candidate.service.CandidateProfileService;
import owoke.staffmatch.backend.common.AccessService;
import owoke.staffmatch.backend.user.entity.UserRole;

@RestController
@RequestMapping("/api/v1/candidate/profile")
public class CandidateProfileController {
    private final AccessService access;
    private final CandidateProfileService profiles;

    public CandidateProfileController(AccessService access, CandidateProfileService profiles) {
        this.access = access;
        this.profiles = profiles;
    }

    @GetMapping
    public Map<String, Object> get(@AuthenticationPrincipal MaxPrincipal principal) {
        return profiles.get(access.require(principal, UserRole.CANDIDATE));
    }

    @PutMapping
    public Map<String, Object> put(@AuthenticationPrincipal MaxPrincipal principal,
                                   @Valid @RequestBody CandidateProfileRequest request) {
        return profiles.save(access.require(principal, UserRole.CANDIDATE), request);
    }
}
