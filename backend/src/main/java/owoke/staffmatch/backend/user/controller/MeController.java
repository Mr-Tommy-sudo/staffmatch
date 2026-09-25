package owoke.staffmatch.backend.user.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import owoke.staffmatch.backend.auth.model.MaxPrincipal;
import owoke.staffmatch.backend.user.dto.ChooseRoleRequest;
import owoke.staffmatch.backend.user.dto.MeResponse;
import owoke.staffmatch.backend.user.mapper.UserMapper;
import owoke.staffmatch.backend.user.service.UserService;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserService userService;
    private final UserMapper userMapper;

    public MeController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public MeResponse getMe(@AuthenticationPrincipal MaxPrincipal principal) {
        return userMapper.toMeResponse(userService.getOrCreate(principal.maxUserId()));
    }

    @PutMapping("/role")
    public MeResponse chooseRole(
            @AuthenticationPrincipal MaxPrincipal principal,
            @Valid @RequestBody ChooseRoleRequest request
    ) {
        return userMapper.toMeResponse(userService.chooseRole(principal.maxUserId(), request.role()));
    }
}
