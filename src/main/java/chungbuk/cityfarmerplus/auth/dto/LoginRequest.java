package chungbuk.cityfarmerplus.auth.dto;

import chungbuk.cityfarmerplus.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(max = 30, message = "아이디는 30자 이하여야 합니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(max = 64, message = "비밀번호는 64자 이하여야 합니다.")
        @Utf8ByteLength(
                max = 72,
                message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
        )
        String password
) {
}
