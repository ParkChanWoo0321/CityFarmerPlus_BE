package chungbuk.cityfarmerplus.application.dto;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record JobApplicationResponse(
        Long id,
        Long jobPostingId,
        String postingTitle,
        String farmName,
        ChungbukCityCounty cityCounty,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        JobApplication.ApplicationStatus status,
        JobApplication.FarmOpinion farmOpinion,
        String farmOpinionNote,
        int wageAmount,
        String wageUnit,
        Instant createdAt,
        Instant withdrawnAt,
        Instant matchedAt
) {

    public static JobApplicationResponse from(JobApplication application) {
        var posting = application.getJobPosting();
        return new JobApplicationResponse(
                application.getId(),
                posting.getId(),
                posting.getTitle(),
                posting.getFarmProfile().getFarmName(),
                posting.getFarmProfile().getCityCounty(),
                posting.getWorkDate(),
                posting.getStartTime(),
                posting.getEndTime(),
                application.getStatus(),
                application.getFarmOpinion(),
                application.getFarmOpinionNote(),
                posting.getWageAmount(),
                posting.getWageUnit().name(),
                application.getCreatedAt(),
                application.getWithdrawnAt(),
                application.getMatchedAt()
        );
    }
}
