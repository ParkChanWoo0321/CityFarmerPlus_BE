package chungbuk.cityfarmerplus.auth.dto;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserProfileUpdateRequest(
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "이름은 비어 있을 수 없습니다."
        )
        String name,

        @Pattern(
                regexp = "^$|^(?=(?:\\D*\\d){10,11}\\D*$)\\d{2,3}-?\\d{3,4}-?\\d{4}$",
                message = "연락처는 숫자 10~11자리여야 합니다."
        )
        String phoneNumber,

        @PastOrPresent(message = "생년월일은 미래일 수 없습니다.")
        LocalDate birthDate,

        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address
) {
}
