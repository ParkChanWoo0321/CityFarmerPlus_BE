package chungbuk.cityfarmerplus.auth.dto;

import chungbuk.cityfarmerplus.auth.entity.User;

import java.time.LocalDate;

public record UserResponse(
        Long id,
        String loginId,
        String name,
        String phoneNumber,
        LocalDate birthDate,
        String address,
        User.UserType userType,
        User.AccountStatus accountStatus
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getPhoneNumber(),
                user.getBirthDate(),
                user.getAddress(),
                user.getUserType(),
                user.getAccountStatus()
        );
    }
}
