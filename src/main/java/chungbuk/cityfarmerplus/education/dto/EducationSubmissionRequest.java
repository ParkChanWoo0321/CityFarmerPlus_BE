package chungbuk.cityfarmerplus.education.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record EducationSubmissionRequest(
        @NotNull(message = "교육 과정 ID를 입력해야 합니다.")
        Long courseId,

        @NotNull(message = "교육 이수일을 입력해야 합니다.")
        @PastOrPresent(message = "교육 이수일은 미래일 수 없습니다.")
        LocalDate completionDate,

        @NotNull(message = "교육 이수 시간을 입력해야 합니다.")
        @Min(value = 8, message = "교육 이수 시간은 8시간 이상이어야 합니다.")
        @Max(value = 1000, message = "교육 이수 시간은 1000시간 이하여야 합니다.")
        Integer completionHours
) {
}
