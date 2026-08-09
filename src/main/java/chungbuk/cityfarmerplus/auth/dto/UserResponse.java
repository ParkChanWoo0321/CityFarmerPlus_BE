package chungbuk.cityfarmerplus.auth.dto;

import chungbuk.cityfarmerplus.auth.entity.User;

public record UserResponse(
        Long id,
        String loginId,
        String name,
        User.UserType userType,
        User.AccountStatus accountStatus
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getUserType(),
                user.getAccountStatus()
        );
    }
}
