package chungbuk.cityfarmerplus.admin.work.dto;

import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttendanceCorrectionRequest(
        @NotNull(message = "정정할 출결 상태를 입력해야 합니다.")
        WorkAssignment.AttendanceStatus status,

        @NotBlank(message = "정정 사유를 입력해야 합니다.")
        @Size(max = 1000, message = "정정 사유는 1000자 이하여야 합니다.")
        String reason
) {
}
