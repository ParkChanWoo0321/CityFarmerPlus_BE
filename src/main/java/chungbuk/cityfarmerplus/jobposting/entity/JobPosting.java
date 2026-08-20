package chungbuk.cityfarmerplus.jobposting.entity;

import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "job_postings",
        indexes = {
                @Index(name = "idx_job_postings_status_date", columnList = "status,work_date"),
                @Index(name = "idx_job_postings_farm", columnList = "farm_profile_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_profile_id", nullable = false, updatable = false)
    private FarmProfile farmProfile;

    @Column(nullable = false, length = 50)
    private String crop;

    @Column(name = "work_type", nullable = false, length = 100)
    private String workType;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "meeting_place", nullable = false, length = 255)
    private String meetingPlace;

    @Column(name = "wage_amount", nullable = false)
    private int wageAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "wage_unit", nullable = false, length = 20)
    private WageUnit wageUnit;

    @Column(length = 1000)
    private String supplies;

    @Column(length = 2000)
    private String precautions;

    @Column(name = "farm_message", length = 1000)
    private String farmMessage;

    @Column(name = "applicant_preference", length = 1000)
    private String applicantPreference;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Column(name = "beginner_guide", length = 2000)
    private String beginnerGuide;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobPostingStatus status;

    @Column(name = "review_requested_at")
    private Instant reviewRequestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static JobPosting createDraft(
            FarmProfile farmProfile,
            JobPostingDetails details
    ) {
        if (farmProfile == null) {
            throw new IllegalArgumentException("농가 프로필은 필수입니다.");
        }
        if (farmProfile.getStatus() != FarmProfile.FarmProfileStatus.APPROVED) {
            throw new IllegalArgumentException("승인된 농가만 공고를 작성할 수 있습니다.");
        }
        JobPosting posting = new JobPosting();
        posting.farmProfile = farmProfile;
        posting.apply(details);
        posting.status = JobPostingStatus.DRAFT;
        return posting;
    }

    public void updateDraft(JobPostingDetails details) {
        requireStatus(JobPostingStatus.DRAFT, "초안 상태의 공고만 농가가 수정할 수 있습니다.");
        apply(details);
        reviewRequestedAt = null;
    }

    public void updateByAdmin(JobPostingDetails details) {
        if (status != JobPostingStatus.DRAFT
                && status != JobPostingStatus.PENDING_REVIEW
                && status != JobPostingStatus.OPEN) {
            throw new IllegalStateException("초안, 승인 대기 또는 모집 중인 공고만 수정할 수 있습니다.");
        }
        apply(details);
    }

    public void updateApplicantPreference(String preference) {
        if (status != JobPostingStatus.OPEN) {
            throw new IllegalStateException("모집 중인 공고에서만 희망 지원자 조건을 수정할 수 있습니다.");
        }
        applicantPreference = normalizeOptionalText(
                preference,
                "희망 지원자 조건",
                1000
        );
    }

    public void submitForReview(Instant now) {
        requireTimestamp(now, "심사 요청 시각");
        requireStatus(JobPostingStatus.DRAFT, "초안 상태의 공고만 심사를 요청할 수 있습니다.");
        status = JobPostingStatus.PENDING_REVIEW;
        reviewRequestedAt = now;
    }

    public void withdrawReview() {
        requireStatus(JobPostingStatus.PENDING_REVIEW, "승인 대기 공고만 심사를 철회할 수 있습니다.");
        status = JobPostingStatus.DRAFT;
    }

    public void approve(Instant now) {
        requireTimestamp(now, "승인 시각");
        requireStatus(JobPostingStatus.PENDING_REVIEW, "승인 대기 공고만 승인할 수 있습니다.");
        status = JobPostingStatus.OPEN;
        approvedAt = now;
    }

    public void reject() {
        requireStatus(JobPostingStatus.PENDING_REVIEW, "승인 대기 공고만 반려할 수 있습니다.");
        status = JobPostingStatus.DRAFT;
    }

    public void close(Instant now) {
        requireTimestamp(now, "마감 시각");
        requireStatus(JobPostingStatus.OPEN, "모집 중인 공고만 마감할 수 있습니다.");
        status = JobPostingStatus.CLOSED;
        closedAt = now;
    }

    public void closeWhenCapacityReached(int matchedCount, Instant now) {
        if (status == JobPostingStatus.OPEN && matchedCount >= capacity) {
            close(now);
        }
    }

    public void cancel(Instant now) {
        requireTimestamp(now, "취소 시각");
        if (status == JobPostingStatus.CANCELLED || status == JobPostingStatus.WORK_COMPLETED) {
            throw new IllegalStateException("이미 취소되었거나 완료된 공고입니다.");
        }
        status = JobPostingStatus.CANCELLED;
        cancelledAt = now;
    }

    public void markWorkCompleted(Instant now) {
        requireTimestamp(now, "근무 완료 시각");
        requireStatus(JobPostingStatus.CLOSED, "마감된 공고만 근무 완료 처리할 수 있습니다.");
        status = JobPostingStatus.WORK_COMPLETED;
        closedAt = closedAt == null ? now : closedAt;
    }

    public void reopenAfterAttendanceCorrection() {
        requireStatus(JobPostingStatus.WORK_COMPLETED, "완료된 공고만 근무 상태를 되돌릴 수 있습니다.");
        status = JobPostingStatus.CLOSED;
    }

    public boolean isVisibleToUrbanFarmers() {
        return status == JobPostingStatus.OPEN;
    }

    public boolean isAcceptingApplications(LocalDate today, LocalTime now) {
        return isVisibleToUrbanFarmers()
                && farmProfile.getStatus() == FarmProfile.FarmProfileStatus.APPROVED
                && farmProfile.getOwner().isActive()
                && (workDate.isAfter(today)
                || workDate.isEqual(today) && startTime.isAfter(now));
    }

    private void apply(JobPostingDetails details) {
        if (details == null) {
            throw new IllegalArgumentException("공고 상세 정보는 필수입니다.");
        }
        crop = details.crop();
        workType = details.workType();
        workDate = details.workDate();
        startTime = details.startTime();
        endTime = details.endTime();
        capacity = details.capacity();
        meetingPlace = details.meetingPlace();
        wageAmount = details.wageAmount();
        wageUnit = details.wageUnit();
        supplies = details.supplies();
        precautions = details.precautions();
        farmMessage = details.farmMessage();
        applicantPreference = details.applicantPreference();
        title = details.title();
        description = details.description();
        beginnerGuide = details.beginnerGuide();
    }

    private void requireStatus(JobPostingStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private void requireTimestamp(Instant timestamp, String fieldName) {
        if (timestamp == null) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }
    }

    private String normalizeOptionalText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "은(는) " + maxLength + "자 이하여야 합니다."
            );
        }
        return normalized;
    }

    public enum JobPostingStatus {
        DRAFT,
        PENDING_REVIEW,
        OPEN,
        CLOSED,
        CANCELLED,
        WORK_COMPLETED
    }

    public enum WageUnit {
        HOURLY,
        DAILY
    }
}
