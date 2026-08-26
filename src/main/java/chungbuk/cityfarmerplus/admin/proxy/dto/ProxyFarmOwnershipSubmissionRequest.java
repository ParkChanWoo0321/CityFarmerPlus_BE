package chungbuk.cityfarmerplus.admin.proxy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProxyFarmOwnershipSubmissionRequest(
        @NotBlank(message = "대리 등록 사유를 입력해야 합니다.")
        @Size(max = 1000, message = "대리 등록 사유는 1000자 이하여야 합니다.")
        String reason
) {
}
