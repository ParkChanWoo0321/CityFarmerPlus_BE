package chungbuk.cityfarmerplus.jobposting.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "job_posting_reviews",
        indexes = @Index(name = "idx_job_posting_reviews_posting", columnList = "job_posting_id,created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false, updatable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_user_id", nullable = false, updatable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReviewAction action;

    @Column(length = 1000, updatable = false)
    private String reason;

    @Column(name = "title_snapshot", nullable = false, length = 150, updatable = false)
    private String titleSnapshot;

    @Column(name = "description_snapshot", nullable = false, length = 5000, updatable = false)
    private String descriptionSnapshot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static JobPostingReview record(
            JobPosting posting,
            User reviewer,
            ReviewAction action,
            String reason
    ) {
        if (posting == null) {
            throw new IllegalArgumentException("심사 대상 공고는 필수입니다.");
        }
        if (reviewer == null
                || reviewer.getUserType() != User.UserType.CENTER_ADMIN
                || !reviewer.isActive()) {
            throw new IllegalArgumentException(
                    "활성 상태인 담당자만 공고 심사 이력을 기록할 수 있습니다."
            );
        }
        if (action == null) {
            throw new IllegalArgumentException("심사 처리 유형은 필수입니다.");
        }
        String normalizedReason = normalizeReason(reason);
        if (action == ReviewAction.REJECTED && normalizedReason == null) {
            throw new IllegalArgumentException("공고 반려 사유는 필수입니다.");
        }
        JobPostingReview review = new JobPostingReview();
        review.jobPosting = posting;
        review.reviewer = reviewer;
        review.action = action;
        review.reason = normalizedReason;
        review.titleSnapshot = posting.getTitle();
        review.descriptionSnapshot = posting.getDescription();
        return review;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException(
                    "심사 사유는 1000자 이하여야 합니다."
            );
        }
        return normalized;
    }

    public enum ReviewAction {
        EDITED,
        APPROVED,
        REJECTED,
        CLOSED,
        CANCELLED
    }
}
