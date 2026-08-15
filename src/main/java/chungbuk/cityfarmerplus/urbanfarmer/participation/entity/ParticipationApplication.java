package chungbuk.cityfarmerplus.urbanfarmer.participation.entity;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "urban_farmer_participation_applications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_participation_application_user_year",
                columnNames = {"urban_farmer_user_id", "program_year"}
        ),
        indexes = {
                @Index(name = "idx_participation_app_status", columnList = "status"),
                @Index(name = "idx_participation_app_submitted", columnList = "submitted_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipationApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "urban_farmer_user_id", nullable = false, updatable = false)
    private User urbanFarmer;

    @Column(name = "program_year", nullable = false, updatable = false)
    private int programYear;

    @Column(name = "agricultural_business_registered", nullable = false)
    private boolean agriculturalBusinessRegistered;

    @Column(name = "application_note", length = 1000)
    private String applicationNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ParticipationApplication createDraft(
            User urbanFarmer,
            int programYear,
            boolean agriculturalBusinessRegistered,
            String applicationNote
    ) {
        if (urbanFarmer.getUserType() != User.UserType.URBAN_FARMER) {
            throw new IllegalArgumentException("도시농부 사용자만 사업참여 신청을 만들 수 있습니다.");
        }
        if (programYear < 2000 || programYear > 2100) {
            throw new IllegalArgumentException("사업연도가 올바르지 않습니다.");
        }
        ParticipationApplication application = new ParticipationApplication();
        application.urbanFarmer = urbanFarmer;
        application.programYear = programYear;
        application.agriculturalBusinessRegistered = agriculturalBusinessRegistered;
        application.applicationNote = applicationNote;
        application.status = ParticipationStatus.DRAFT;
        return application;
    }

    public void updateDraft(
            boolean agriculturalBusinessRegistered,
            String applicationNote
    ) {
        if (status != ParticipationStatus.DRAFT
                && status != ParticipationStatus.REJECTED) {
            throw new IllegalStateException("초안 또는 반려 상태에서만 신청서를 수정할 수 있습니다.");
        }
        this.agriculturalBusinessRegistered = agriculturalBusinessRegistered;
        this.applicationNote = applicationNote;
        this.status = ParticipationStatus.DRAFT;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.rejectionReason = null;
    }

    public void submit(Instant now) {
        if (status != ParticipationStatus.DRAFT) {
            throw new IllegalStateException("초안 상태에서만 신청서를 제출할 수 있습니다.");
        }
        status = ParticipationStatus.SUBMITTED;
        submittedAt = now;
    }

    public void updateSubmitted(
            boolean agriculturalBusinessRegistered,
            String applicationNote
    ) {
        if (status != ParticipationStatus.SUBMITTED) {
            throw new IllegalStateException("제출 상태의 신청서만 제출 정보를 수정할 수 있습니다.");
        }
        this.agriculturalBusinessRegistered = agriculturalBusinessRegistered;
        this.applicationNote = applicationNote;
    }

    public void updateRejected(
            boolean agriculturalBusinessRegistered,
            String applicationNote
    ) {
        if (status != ParticipationStatus.REJECTED) {
            throw new IllegalStateException("반려 상태의 신청서만 반려 정보를 유지하며 수정할 수 있습니다.");
        }
        this.agriculturalBusinessRegistered = agriculturalBusinessRegistered;
        this.applicationNote = applicationNote;
    }

    public void resubmit(Instant now) {
        if (status != ParticipationStatus.REJECTED) {
            throw new IllegalStateException("반려 상태의 신청서만 재제출할 수 있습니다.");
        }
        status = ParticipationStatus.SUBMITTED;
        submittedAt = now;
        reviewedBy = null;
        reviewedAt = null;
        rejectionReason = null;
        cancelledAt = null;
    }

    public void approve(User reviewer, Instant now) {
        validateReviewerAndPending(reviewer);
        status = ParticipationStatus.APPROVED;
        reviewedBy = reviewer;
        reviewedAt = now;
        rejectionReason = null;
    }

    public void reject(User reviewer, String reason, Instant now) {
        validateReviewerAndPending(reviewer);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("반려 사유를 입력해야 합니다.");
        }
        status = ParticipationStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = now;
        rejectionReason = reason;
    }

    public void cancel(Instant now) {
        if (status == ParticipationStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 신청입니다.");
        }
        status = ParticipationStatus.CANCELLED;
        cancelledAt = now;
    }

    /**
     * 회원 탈퇴 시 심사 대기열에 남아서는 안 되는 신청만 취소한다.
     * 승인 이력과 이미 취소된 신청은 그대로 유지하며, 반려 이력도 지우지 않는다.
     */
    public void cancelForAccountWithdrawal(Instant now) {
        if (status != ParticipationStatus.DRAFT
                && status != ParticipationStatus.SUBMITTED
                && status != ParticipationStatus.REJECTED) {
            return;
        }
        status = ParticipationStatus.CANCELLED;
        cancelledAt = now;
    }

    public boolean canDeleteDraft() {
        return status == ParticipationStatus.DRAFT;
    }

    private void validateReviewerAndPending(User reviewer) {
        if (reviewer.getUserType() != User.UserType.CENTER_ADMIN) {
            throw new IllegalArgumentException("담당자만 신청을 심사할 수 있습니다.");
        }
        if (status != ParticipationStatus.SUBMITTED) {
            throw new IllegalStateException("제출 상태의 신청서만 심사할 수 있습니다.");
        }
    }

    public enum ParticipationStatus {
        DRAFT,
        SUBMITTED,
        APPROVED,
        REJECTED,
        CANCELLED
    }
}
