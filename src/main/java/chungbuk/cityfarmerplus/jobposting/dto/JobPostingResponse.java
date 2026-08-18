package chungbuk.cityfarmerplus.jobposting.dto;

import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
import chungbuk.cityfarmerplus.jobposting.entity.JobPostingReview;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record JobPostingResponse(
        Long id,
        Long farmProfileId,
        String farmName,
        ChungbukCityCounty cityCounty,
        String farmAddress,
        String contactNumber,
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
        JobPosting.JobPostingStatus status,
        FarmJobPostingDisplayStatus displayStatus,
        Instant reviewRequestedAt,
        Instant approvedAt,
        Instant closedAt,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt,
        JobPostingReview.ReviewAction latestReviewAction,
        String latestReviewReason,
        Instant latestReviewedAt
) {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public static JobPostingResponse from(JobPosting posting) {
        return from(posting, null);
    }

    public static JobPostingResponse from(
            JobPosting posting,
            JobPostingReview latestReview
    ) {
        return new JobPostingResponse(
                posting.getId(),
                posting.getFarmProfile().getId(),
                posting.getFarmProfile().getFarmName(),
                posting.getFarmProfile().getCityCounty(),
                posting.getFarmProfile().getFarmAddress(),
                posting.getFarmProfile().getContactNumber(),
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
                posting.getStatus(),
                displayStatus(posting, latestReview),
                posting.getReviewRequestedAt(),
                posting.getApprovedAt(),
                posting.getClosedAt(),
                posting.getCancelledAt(),
                posting.getCreatedAt(),
                posting.getUpdatedAt(),
                latestReview == null ? null : latestReview.getAction(),
                latestReview == null ? null : latestReview.getReason(),
                latestReview == null ? null : latestReview.getCreatedAt()
        );
    }

    private static FarmJobPostingDisplayStatus displayStatus(
            JobPosting posting,
            JobPostingReview latestReview
    ) {
        return switch (posting.getStatus()) {
            case DRAFT -> hasCurrentRejection(posting, latestReview)
                    ? FarmJobPostingDisplayStatus.REJECTED
                    : FarmJobPostingDisplayStatus.DRAFT;
            case PENDING_REVIEW -> FarmJobPostingDisplayStatus.PENDING;
            case OPEN -> hasWorkStarted(posting)
                    ? FarmJobPostingDisplayStatus.CLOSED
                    : FarmJobPostingDisplayStatus.APPROVED;
            case CLOSED, WORK_COMPLETED -> FarmJobPostingDisplayStatus.CLOSED;
            case CANCELLED -> FarmJobPostingDisplayStatus.CANCELLED;
        };
    }

    private static boolean hasWorkStarted(JobPosting posting) {
        ZonedDateTime now = ZonedDateTime.now(SERVICE_ZONE);
        return posting.getWorkDate().isBefore(now.toLocalDate())
                || posting.getWorkDate().isEqual(now.toLocalDate())
                && !posting.getStartTime().isAfter(now.toLocalTime());
    }

    private static boolean hasCurrentRejection(
            JobPosting posting,
            JobPostingReview latestReview
    ) {
        return posting.getReviewRequestedAt() != null
                && latestReview != null
                && latestReview.getAction() == JobPostingReview.ReviewAction.REJECTED
                && latestReview.getCreatedAt() != null
                && !latestReview.getCreatedAt().isBefore(
                        posting.getReviewRequestedAt()
                );
    }
}
