package chungbuk.cityfarmerplus.auth.dto;

import chungbuk.cityfarmerplus.auth.entity.User;
import lombok.Getter;

@Getter
public class UserResponse {

    private final Long id;
    private final String loginId;
    private final String name;
    private final User.UserType userType;

    public UserResponse(User user) {
        this.id = user.getId();
        this.loginId = user.getLoginId();
        this.name = user.getName();
        this.userType = user.getUserType();
    }
}
