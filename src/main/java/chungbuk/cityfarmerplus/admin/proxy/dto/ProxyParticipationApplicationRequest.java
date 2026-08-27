package chungbuk.cityfarmerplus.admin.proxy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProxyParticipationApplicationRequest(
        @Min(value = 2000, message = "사업연도는 2000 이상이어야 합니다.")
        @Max(value = 2100, message = "사업연도는 2100 이하여야 합니다.")
        Integer programYear,

        @NotNull(message = "농업경영체 등록 여부를 입력해야 합니다.")
        Boolean agriculturalBusinessRegistered,

        @Size(max = 1000, message = "신청 특이사항은 1000자 이하여야 합니다.")
        String applicationNote,

        @NotBlank(message = "대리 등록 사유를 입력해야 합니다.")
        @Size(max = 1000, message = "대리 등록 사유는 1000자 이하여야 합니다.")
        String reason
) {
}
