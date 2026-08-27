package chungbuk.cityfarmerplus.education.progress.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record EducationProgressEventRequest(
        @NotBlank(message = "교육 제공자 코드는 필수입니다.")
        @Pattern(
                regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$",
                message = "교육 제공자 코드 형식이 올바르지 않습니다."
        )
        String provider,

        @NotBlank(message = "교육 진도 이벤트 ID는 필수입니다.")
        @Size(max = 100, message = "교육 진도 이벤트 ID는 100자 이하여야 합니다.")
        @Pattern(
                regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$",
                message = "교육 진도 이벤트 ID 형식이 올바르지 않습니다."
        )
        String eventId,

        @NotBlank(message = "외부 수강 등록 ID는 필수입니다.")
        @Size(max = 100, message = "외부 수강 등록 ID는 100자 이하여야 합니다.")
        @Pattern(
                regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$",
                message = "외부 수강 등록 ID 형식이 올바르지 않습니다."
        )
        String externalEnrollmentId,

        @NotNull(message = "도시농부 회원 ID는 필수입니다.")
        @Positive(message = "도시농부 회원 ID는 양수여야 합니다.")
        Long urbanFarmerId,

        @NotNull(message = "교육 과정 ID는 필수입니다.")
        @Positive(message = "교육 과정 ID는 양수여야 합니다.")
        Long courseId,

        @NotNull(message = "전체 교육 시간은 필수입니다.")
        @Positive(message = "전체 교육 시간은 1분 이상이어야 합니다.")
        @Max(value = 525600, message = "전체 교육 시간은 525600분 이하여야 합니다.")
        Integer totalMinutes,

        @NotNull(message = "현재 수강 시간은 필수입니다.")
        @PositiveOrZero(message = "현재 수강 시간은 0분 이상이어야 합니다.")
        @Max(value = 525600, message = "현재 수강 시간은 525600분 이하여야 합니다.")
        Integer completedMinutes,

        @NotNull(message = "교육 진도 발생 시각은 필수입니다.")
        Instant occurredAt
) {
}
