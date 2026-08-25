package chungbuk.cityfarmerplus.jobposting.dto;

import chungbuk.cityfarmerplus.application.entity.JobApplication;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public record PublicJobPostingResponse(
        Long id,
        Long farmProfileId,
        String farmName,
        ChungbukCityCounty cityCounty,
        String crop,
        String workType,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        int capacity,
        String meetingPlace,
        int wageAmount,
        JobPosting.WageUnit wageUnit,
        String supplies,
        String precautions,
        String farmMessage,
        String applicantPreference,
        String title,
        String description,
        String beginnerGuide,
        Instant approvedAt,
        PublicRecruitmentStatus recruitmentStatus,
        boolean acceptingApplications,
        PublicJobApplicationSummary myApplication
) {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public static PublicJobPostingResponse from(JobPosting posting) {
        return from(
                posting,
                null,
                LocalDate.now(SERVICE_ZONE),
                LocalTime.now(SERVICE_ZONE)
        );
    }

    public static PublicJobPostingResponse from(
            JobPosting posting,
            JobApplication myApplication,
            LocalDate today,
            LocalTime now
    ) {
        boolean acceptingApplications = posting.isAcceptingApplications(today, now);
        return new PublicJobPostingResponse(
                posting.getId(),
                posting.getFarmProfile().getId(),
                posting.getFarmProfile().getFarmName(),
                posting.getFarmProfile().getCityCounty(),
                posting.getCrop(),
                posting.getWorkType(),
                posting.getWorkDate(),
                posting.getStartTime(),
                posting.getEndTime(),
                posting.getCapacity(),
                posting.getMeetingPlace(),
                posting.getWageAmount(),
                posting.getWageUnit(),
                posting.getSupplies(),
                posting.getPrecautions(),
                posting.getFarmMessage(),
                posting.getApplicantPreference(),
                posting.getTitle(),
                posting.getDescription(),
                posting.getBeginnerGuide(),
                posting.getApprovedAt(),
                acceptingApplications
                        ? PublicRecruitmentStatus.OPEN
                        : PublicRecruitmentStatus.CLOSED,
                acceptingApplications,
                myApplication == null
                        ? null
                        : PublicJobApplicationSummary.from(myApplication)
        );
    }
}
