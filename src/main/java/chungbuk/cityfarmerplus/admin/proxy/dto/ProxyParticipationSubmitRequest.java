package chungbuk.cityfarmerplus.admin.proxy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProxyParticipationSubmitRequest(
        @NotBlank(message = "대리 제출 사유를 입력해야 합니다.")
        @Size(max = 1000, message = "대리 제출 사유는 1000자 이하여야 합니다.")
        String reason
) {
}
