package owoke.staffmatch.backend.user.mapper;

import org.springframework.stereotype.Component;
import owoke.staffmatch.backend.user.dto.MeResponse;
import owoke.staffmatch.backend.user.entity.UserAccount;

@Component
public class UserMapper {

    public MeResponse toMeResponse(UserAccount user) {
        return new MeResponse(user.getId(), user.getRole());
    }
}
