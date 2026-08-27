package chungbuk.cityfarmerplus.admin.education.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EducationApproveRequest(
        @NotNull(message = "인정 교육 시간을 입력해야 합니다.")
        @Min(value = 1, message = "인정 교육 시간은 1시간 이상이어야 합니다.")
        Integer recognizedHours
) {
}
