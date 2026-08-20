package chungbuk.cityfarmerplus.farm.ownership.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
        name = "farm_ownership_submissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ownership_submission_attempt",
                columnNames = {"farm_profile_id", "attempt_number"}
        ),
        indexes = {
                @Index(
                        name = "idx_ownership_submissions_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_ownership_submissions_submitted_at",
                        columnList = "submitted_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmOwnershipSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_profile_id", nullable = false, updatable = false)
    private FarmProfile farmProfile;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "farm_name_snapshot", length = 100, updatable = false)
    private String farmNameSnapshot;

    @Column(name = "representative_name_snapshot", length = 50, updatable = false)
    private String representativeNameSnapshot;

    @Column(name = "farm_address_snapshot", length = 255, updatable = false)
    private String farmAddressSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "city_county_snapshot", length = 30, updatable = false)
    private ChungbukCityCounty cityCountySnapshot;

    @Column(name = "business_number_snapshot", length = 10, updatable = false)
    private String businessRegistrationNumberSnapshot;

    @Column(name = "farm_area_pyeong_snapshot", updatable = false)
    private Integer farmAreaPyeongSnapshot;

    @OneToMany(
            mappedBy = "submission",
            cascade = CascadeType.PERSIST,
            orphanRemoval = false
    )
    @OrderBy("displayOrder ASC")
    private List<FarmOwnershipDocument> documents = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id")
    private User reviewer;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    public static FarmOwnershipSubmission createPending(
            FarmProfile farmProfile,
            int attemptNumber
    ) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("제출 회차는 1 이상이어야 합니다.");
        }

        FarmOwnershipSubmission submission = new FarmOwnershipSubmission();
        submission.farmProfile = farmProfile;
        submission.attemptNumber = attemptNumber;
        submission.status = SubmissionStatus.PENDING_REVIEW;
        submission.farmNameSnapshot = farmProfile.getFarmName();
        submission.representativeNameSnapshot = farmProfile.getRepresentativeName();
        submission.farmAddressSnapshot = farmProfile.getFarmAddress();
        submission.cityCountySnapshot = farmProfile.getCityCounty();
        submission.businessRegistrationNumberSnapshot =
                farmProfile.getBusinessRegistrationNumber();
        submission.farmAreaPyeongSnapshot = farmProfile.getFarmAreaPyeong();
        return submission;
    }

    public void addDocument(
            String originalFilename,
            String storageKey,
            String contentType,
            long sizeBytes,
            String sha256
    ) {
        FarmOwnershipDocument document = FarmOwnershipDocument.create(
                this,
                documents.size(),
                originalFilename,
                storageKey,
                contentType,
                sizeBytes,
                sha256
        );
        documents.add(document);
    }

    public List<FarmOwnershipDocument> getDocuments() {
        return Collections.unmodifiableList(documents);
    }

    public void approve(User reviewer, Instant reviewedAt) {
        validateReview(reviewer, reviewedAt);
        status = SubmissionStatus.APPROVED;
        this.reviewer = reviewer;
        this.reviewedAt = reviewedAt;
        rejectionReason = null;
    }

    public void reject(User reviewer, Instant reviewedAt, String rejectionReason) {
        validateReview(reviewer, reviewedAt);
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 필수입니다.");
        }
        status = SubmissionStatus.REJECTED;
        this.reviewer = reviewer;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = rejectionReason;
    }

    private void validateReview(User reviewer, Instant reviewedAt) {
        if (status != SubmissionStatus.PENDING_REVIEW) {
            throw new IllegalStateException("심사 대기 중인 제출만 처리할 수 있습니다.");
        }
        if (reviewer == null || reviewer.getUserType() != User.UserType.CENTER_ADMIN) {
            throw new IllegalArgumentException("담당자만 소유 증빙을 심사할 수 있습니다.");
        }
        if (reviewedAt == null) {
            throw new IllegalArgumentException("심사 시각은 필수입니다.");
        }
    }

    public enum SubmissionStatus {
        PENDING_REVIEW,
        APPROVED,
        REJECTED
    }
}
