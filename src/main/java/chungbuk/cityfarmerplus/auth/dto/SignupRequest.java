package chungbuk.cityfarmerplus.auth.dto;

import chungbuk.cityfarmerplus.auth.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class SignupRequest {

    @NotBlank(message = "아이디는 필수입니다.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotNull(message = "사용자 유형은 필수입니다.")
    private User.UserType userType;
}
