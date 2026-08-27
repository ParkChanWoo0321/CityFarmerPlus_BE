package chungbuk.cityfarmerplus.admin.jobposting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobPostingRejectRequest(
        @NotBlank(message = "반려 사유를 입력해야 합니다.")
        @Size(max = 1000, message = "반려 사유는 1000자 이하여야 합니다.")
        String reason
) {
}
