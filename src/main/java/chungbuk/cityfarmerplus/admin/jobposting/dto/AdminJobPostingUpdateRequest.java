package chungbuk.cityfarmerplus.admin.jobposting.dto;

import chungbuk.cityfarmerplus.jobposting.dto.JobPostingTextLimits;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingDetails;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AdminJobPostingUpdateRequest(
        @NotBlank(message = "변경 사유를 입력해야 합니다.")
        @Size(max = 1000, message = "변경 사유는 1000자 이하여야 합니다.")
        String reason,

        @NotBlank @Size(max = 50) String crop,
        @NotBlank @Size(max = 100) String workType,
        @NotNull @FutureOrPresent LocalDate workDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(1) @Max(1000) int capacity,
        @NotBlank @Size(max = 255) String meetingPlace,
        @Min(1) @Max(100_000_000) int wageAmount,
        @NotNull JobPosting.WageUnit wageUnit,
        @Size(max = JobPostingTextLimits.SUPPLIES_MAX_LENGTH) String supplies,
        @Size(max = JobPostingTextLimits.PRECAUTIONS_MAX_LENGTH) String precautions,
        @Size(max = 1000) String farmMessage,
        @Size(max = 1000) String applicantPreference,
        @NotBlank @Size(max = JobPostingTextLimits.TITLE_MAX_LENGTH) String title,
        @NotBlank @Size(max = JobPostingTextLimits.DESCRIPTION_MAX_LENGTH) String description,
        @Size(max = JobPostingTextLimits.BEGINNER_GUIDE_MAX_LENGTH) String beginnerGuide
) {

    public JobPostingDetails toDetails() {
        return new JobPostingDetails(
                crop.trim(),
                workType.trim(),
                workDate,
                startTime,
                endTime,
                capacity,
                meetingPlace.trim(),
                wageAmount,
                wageUnit,
                trimToNull(supplies),
                trimToNull(precautions),
                trimToNull(farmMessage),
                trimToNull(applicantPreference),
                title.trim(),
                description.trim(),
                trimToNull(beginnerGuide)
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
