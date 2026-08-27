package chungbuk.cityfarmerplus.admin.proxy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProxyUrbanFarmerProfileRequest(
        @NotNull(message = "농업경영체 등록 여부를 입력해야 합니다.")
        Boolean agriculturalBusinessRegistered,

        @NotNull(message = "활동 경험 횟수를 입력해야 합니다.")
        @Min(value = 0, message = "활동 경험 횟수는 0 이상이어야 합니다.")
        @Max(value = 10000, message = "활동 경험 횟수는 10000 이하여야 합니다.")
        Integer experienceCount,

        @Size(max = 1000, message = "특이사항은 1000자 이하여야 합니다.")
        String notes,

        @NotBlank(message = "대리 등록 사유를 입력해야 합니다.")
        @Size(max = 1000, message = "대리 등록 사유는 1000자 이하여야 합니다.")
        String reason
) {
}
