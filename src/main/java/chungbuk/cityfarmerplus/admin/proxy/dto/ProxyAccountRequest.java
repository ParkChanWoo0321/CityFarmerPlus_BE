package chungbuk.cityfarmerplus.admin.proxy.dto;

import chungbuk.cityfarmerplus.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProxyAccountRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Pattern(
                regexp = "^[a-z0-9_]{4,30}$",
                message = "아이디는 4~30자의 영문 소문자, 숫자, 밑줄만 사용할 수 있습니다."
        )
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        @Utf8ByteLength(
                max = 72,
                message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
        )
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Pattern(
                regexp = "^$|^(?=(?:\\D*\\d){10,11}\\D*$)\\d{2,3}-?\\d{3,4}-?\\d{4}$",
                message = "연락처는 숫자 10~11자리여야 합니다."
        )
        String phoneNumber,

        @PastOrPresent(message = "생년월일은 미래일 수 없습니다.")
        LocalDate birthDate,

        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address,

        @NotBlank(message = "대리 등록 사유를 입력해야 합니다.")
        @Size(max = 1000, message = "대리 등록 사유는 1000자 이하여야 합니다.")
        String reason
) {
}
