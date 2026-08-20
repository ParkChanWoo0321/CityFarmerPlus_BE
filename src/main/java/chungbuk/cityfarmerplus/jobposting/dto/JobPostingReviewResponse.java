package chungbuk.cityfarmerplus.jobposting.dto;

import chungbuk.cityfarmerplus.jobposting.entity.JobPostingReview;

import java.time.Instant;

public record JobPostingReviewResponse(
        Long id,
        Long reviewerUserId,
        String reviewerName,
        JobPostingReview.ReviewAction action,
        String reason,
        String titleSnapshot,
        String descriptionSnapshot,
        Instant createdAt
) {

    public static JobPostingReviewResponse from(JobPostingReview review) {
        return new JobPostingReviewResponse(
                review.getId(),
                review.getReviewer().getId(),
                review.getReviewer().getName(),
                review.getAction(),
                review.getReason(),
                review.getTitleSnapshot(),
                review.getDescriptionSnapshot(),
                review.getCreatedAt()
        );
    }
}
