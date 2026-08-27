package chungbuk.cityfarmerplus.admin.dto;

import chungbuk.cityfarmerplus.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CenterAdminProvisioningRequest(
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
        String name
) {
}
