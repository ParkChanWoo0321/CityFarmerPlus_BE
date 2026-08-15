package chungbuk.cityfarmerplus.auth.dto;

import chungbuk.cityfarmerplus.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;

public record AccountWithdrawalRequest(
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Utf8ByteLength(
                max = 72,
                message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
        )
        String password
) {
}
