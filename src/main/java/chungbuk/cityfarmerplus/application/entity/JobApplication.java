package chungbuk.cityfarmerplus.application.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.jobposting.entity.JobPosting;
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
import java.time.LocalDate;

@Entity
@Table(
        name = "job_applications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_applications_posting_farmer",
                columnNames = {"job_posting_id", "urban_farmer_user_id"}
        ),
        indexes = {
                @Index(name = "idx_job_applications_posting_status", columnList = "job_posting_id,status"),
                @Index(name = "idx_job_applications_farmer", columnList = "urban_farmer_user_id,status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false, updatable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "urban_farmer_user_id", nullable = false, updatable = false)
    private User urbanFarmer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "farm_opinion", nullable = false, length = 30)
    private FarmOpinion farmOpinion;

    @Column(name = "farm_opinion_note", length = 1000)
    private String farmOpinionNote;

    @Column(name = "education_verified_at", nullable = false)
    private Instant educationVerifiedAt;

    @Column(name = "preferred_regions_snapshot", length = 500)
    private String preferredRegionsSnapshot;

    @Column(name = "available_days_snapshot", length = 200)
    private String availableDaysSnapshot;

    @Column(name = "preferred_start_date_snapshot")
    private LocalDate preferredStartDateSnapshot;

    @Column(name = "preferred_end_date_snapshot")
    private LocalDate preferredEndDateSnapshot;

    @Column(name = "available_work_types_snapshot", length = 2000)
    private String availableWorkTypesSnapshot;

    @Column(name = "can_travel_snapshot")
    private Boolean canTravelSnapshot;

    @Column(name = "experience_count_snapshot", nullable = false)
    private int experienceCountSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_user_id")
    private User confirmedBy;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static JobApplication apply(
            JobPosting posting,
            User urbanFarmer,
            Instant educationVerifiedAt,
            String preferredRegionsSnapshot,
            String availableDaysSnapshot,
            LocalDate preferredStartDateSnapshot,
            LocalDate preferredEndDateSnapshot,
            String availableWorkTypesSnapshot,
            Boolean canTravelSnapshot,
            int experienceCountSnapshot
    ) {
        if (!posting.isVisibleToUrbanFarmers()) {
            throw new IllegalStateException("모집 중인 공고에만 지원할 수 있습니다.");
        }
        if (urbanFarmer.getUserType() != User.UserType.URBAN_FARMER) {
            throw new IllegalArgumentException("도시농부만 공고에 지원할 수 있습니다.");
        }
        JobApplication application = new JobApplication();
        application.jobPosting = posting;
        application.urbanFarmer = urbanFarmer;
        application.status = ApplicationStatus.APPLIED;
        application.farmOpinion = FarmOpinion.NONE;
        application.educationVerifiedAt = educationVerifiedAt;
        application.preferredRegionsSnapshot = preferredRegionsSnapshot;
        application.availableDaysSnapshot = availableDaysSnapshot;
        application.preferredStartDateSnapshot = preferredStartDateSnapshot;
        application.preferredEndDateSnapshot = preferredEndDateSnapshot;
        application.availableWorkTypesSnapshot = availableWorkTypesSnapshot;
        application.canTravelSnapshot = canTravelSnapshot;
        application.experienceCountSnapshot = experienceCountSnapshot;
        return application;
    }

    public static JobApplication apply(
            JobPosting posting,
            User urbanFarmer,
            Instant educationVerifiedAt,
            String preferredRegionsSnapshot,
            String availableDaysSnapshot,
            int experienceCountSnapshot
    ) {
        return apply(
                posting,
                urbanFarmer,
                educationVerifiedAt,
                preferredRegionsSnapshot,
                availableDaysSnapshot,
                null,
                null,
                null,
                null,
                experienceCountSnapshot
        );
    }

    public void reapply(
            Instant educationVerifiedAt,
            String preferredRegionsSnapshot,
            String availableDaysSnapshot,
            LocalDate preferredStartDateSnapshot,
            LocalDate preferredEndDateSnapshot,
            String availableWorkTypesSnapshot,
            Boolean canTravelSnapshot,
            int experienceCountSnapshot
    ) {
        if (status != ApplicationStatus.WITHDRAWN) {
            throw new IllegalStateException("취소한 지원만 다시 지원할 수 있습니다.");
        }
        if (!jobPosting.isVisibleToUrbanFarmers()) {
            throw new IllegalStateException("모집 중인 공고에만 다시 지원할 수 있습니다.");
        }
        status = ApplicationStatus.APPLIED;
        withdrawnAt = null;
        this.educationVerifiedAt = educationVerifiedAt;
        this.preferredRegionsSnapshot = preferredRegionsSnapshot;
        this.availableDaysSnapshot = availableDaysSnapshot;
        this.preferredStartDateSnapshot = preferredStartDateSnapshot;
        this.preferredEndDateSnapshot = preferredEndDateSnapshot;
        this.availableWorkTypesSnapshot = availableWorkTypesSnapshot;
        this.canTravelSnapshot = canTravelSnapshot;
        this.experienceCountSnapshot = experienceCountSnapshot;
        farmOpinion = FarmOpinion.NONE;
        farmOpinionNote = null;
    }

    public void reapply(
            Instant educationVerifiedAt,
            String preferredRegionsSnapshot,
            String availableDaysSnapshot,
            int experienceCountSnapshot
    ) {
        reapply(
                educationVerifiedAt,
                preferredRegionsSnapshot,
                availableDaysSnapshot,
                preferredStartDateSnapshot,
                preferredEndDateSnapshot,
                availableWorkTypesSnapshot,
                canTravelSnapshot,
                experienceCountSnapshot
        );
    }

    public void reapply(Instant educationVerifiedAt) {
        reapply(
                educationVerifiedAt,
                preferredRegionsSnapshot,
                availableDaysSnapshot,
                preferredStartDateSnapshot,
                preferredEndDateSnapshot,
                availableWorkTypesSnapshot,
                canTravelSnapshot,
                experienceCountSnapshot
        );
    }

    public void withdraw(Instant now) {
        if (status != ApplicationStatus.APPLIED) {
            throw new IllegalStateException("매칭 확정 전 지원만 취소할 수 있습니다.");
        }
        status = ApplicationStatus.WITHDRAWN;
        withdrawnAt = now;
    }

    public void updateFarmOpinion(FarmOpinion opinion, String note) {
        if (status != ApplicationStatus.APPLIED) {
            throw new IllegalStateException("검토 중인 지원자에 대해서만 의견을 남길 수 있습니다.");
        }
        if (!jobPosting.isVisibleToUrbanFarmers()) {
            throw new IllegalStateException("공고 마감 전까지만 농가 의견을 수정할 수 있습니다.");
        }
        farmOpinion = opinion;
        farmOpinionNote = note;
    }

    public void match(User admin, Instant now) {
        if (status != ApplicationStatus.APPLIED) {
            throw new IllegalStateException("지원 완료 상태만 매칭할 수 있습니다.");
        }
        status = ApplicationStatus.MATCHED;
        confirmedBy = admin;
        matchedAt = now;
    }

    public void markNotMatched() {
        if (status == ApplicationStatus.APPLIED) {
            status = ApplicationStatus.NOT_MATCHED;
        }
    }

    public void cancelWithPosting() {
        if (status == ApplicationStatus.APPLIED) {
            status = ApplicationStatus.POSTING_CANCELLED;
        }
    }

    public void cancelWithPostingByAdmin() {
        if (status == ApplicationStatus.APPLIED
                || status == ApplicationStatus.MATCHED
                || status == ApplicationStatus.NO_SHOW) {
            status = ApplicationStatus.POSTING_CANCELLED;
        }
    }

    public void completeWork() {
        if (status != ApplicationStatus.MATCHED) {
            throw new IllegalStateException("매칭된 지원만 근무 완료 처리할 수 있습니다.");
        }
        status = ApplicationStatus.WORK_COMPLETED;
    }

    public void reopenWorkAfterAttendanceCorrection() {
        if (status == ApplicationStatus.WORK_COMPLETED) {
            status = ApplicationStatus.MATCHED;
        }
    }

    public void markNoShow() {
        if (status != ApplicationStatus.MATCHED
                && status != ApplicationStatus.WORK_COMPLETED) {
            throw new IllegalStateException("매칭된 지원만 결근 처리할 수 있습니다.");
        }
        status = ApplicationStatus.NO_SHOW;
    }

    public void reopenAfterNoShow() {
        if (status != ApplicationStatus.NO_SHOW) {
            throw new IllegalStateException("결근 처리된 지원만 근무 예정으로 되돌릴 수 있습니다.");
        }
        status = ApplicationStatus.MATCHED;
    }

    public enum ApplicationStatus {
        APPLIED,
        WITHDRAWN,
        MATCHED,
        NOT_MATCHED,
        POSTING_CANCELLED,
        NO_SHOW,
        WORK_COMPLETED
    }

    public enum FarmOpinion {
        NONE,
        PREFERRED,
        NOT_PREFERRED
    }
}
