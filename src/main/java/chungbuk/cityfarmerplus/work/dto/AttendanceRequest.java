package chungbuk.cityfarmerplus.work.dto;

import chungbuk.cityfarmerplus.work.entity.WorkAssignment;
import jakarta.validation.constraints.NotNull;

public record AttendanceRequest(
        @NotNull(message = "출결 상태는 필수입니다.")
        WorkAssignment.AttendanceStatus status
) {
}
