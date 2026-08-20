package chungbuk.cityfarmerplus.admin.participation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParticipationRejectRequest(
        @NotBlank(message = "반려 사유를 입력해야 합니다.")
        @Size(max = 500, message = "반려 사유는 500자 이하여야 합니다.")
        String reason
) {
}
