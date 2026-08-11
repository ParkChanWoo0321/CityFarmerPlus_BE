package chungbuk.cityfarmerplus.farm.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "farm_profiles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_farm_profiles_owner",
                        columnNames = "owner_user_id"
                )
        },
        indexes = {
                @Index(name = "idx_farm_profiles_status", columnList = "status"),
                @Index(name = "idx_farm_profiles_city_county", columnList = "city_county")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private User owner;

    @Column(name = "farm_name", nullable = false, length = 100)
    private String farmName;

    @Column(name = "representative_name", nullable = false, length = 50)
    private String representativeName;

    @Column(name = "contact_number", nullable = false, length = 11)
    private String contactNumber;

    @Column(name = "farm_address", nullable = false, length = 255)
    private String farmAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "city_county", nullable = false, length = 30)
    private ChungbukCityCounty cityCounty;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "farm_profile_crops",
            joinColumns = @JoinColumn(name = "farm_profile_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_farm_profile_crops",
                    columnNames = {"farm_profile_id", "crop"}
            )
    )
    @OrderColumn(name = "display_order")
    @Column(name = "crop", nullable = false, length = 50)
    private List<String> crops = new ArrayList<>();

    @Column(name = "main_activities", nullable = false, length = 2000)
    private String mainActivities;

    @Column(name = "business_registration_number", length = 10)
    private String businessRegistrationNumber;

    @Column(name = "farm_area_pyeong")
    private Integer farmAreaPyeong;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FarmProfileStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id")
    private User reviewer;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FarmProfile createDraft(
            User owner,
            String farmName,
            String representativeName,
            String contactNumber,
            String farmAddress,
            ChungbukCityCounty cityCounty,
            List<String> crops,
            String mainActivities,
            String businessRegistrationNumber,
            Integer farmAreaPyeong
    ) {
        if (owner.getUserType() != User.UserType.FARM) {
            throw new IllegalArgumentException("농가 사용자만 농가 프로필을 만들 수 있습니다.");
        }

        FarmProfile profile = new FarmProfile();
        profile.owner = owner;
        profile.farmName = farmName;
        profile.representativeName = representativeName;
        profile.contactNumber = contactNumber;
        profile.farmAddress = farmAddress;
        profile.cityCounty = cityCounty;
        profile.crops = new ArrayList<>(crops);
        profile.mainActivities = mainActivities;
        profile.businessRegistrationNumber = businessRegistrationNumber;
        profile.farmAreaPyeong = farmAreaPyeong;
        profile.status = FarmProfileStatus.DRAFT;
        return profile;
    }

    public static FarmProfile createDraft(
            User owner,
            String farmName,
            String representativeName,
            String contactNumber,
            String farmAddress,
            ChungbukCityCounty cityCounty,
            List<String> crops,
            String mainActivities,
            String businessRegistrationNumber
    ) {
        return createDraft(owner, farmName, representativeName, contactNumber,
                farmAddress, cityCounty, crops, mainActivities,
                businessRegistrationNumber, 1);
    }

    public boolean canSubmitOwnershipDocuments() {
        return status == FarmProfileStatus.DRAFT
                || status == FarmProfileStatus.REJECTED;
    }

    public void markOwnershipReviewPending() {
        if (!canSubmitOwnershipDocuments()) {
            throw new IllegalStateException("현재 상태에서는 농가 소유 증빙을 제출할 수 없습니다.");
        }
        status = FarmProfileStatus.PENDING_REVIEW;
        reviewer = null;
        reviewedAt = null;
        rejectionReason = null;
    }

    public boolean canUpdateBasicInformation() {
        return status != FarmProfileStatus.INACTIVE
                && status != FarmProfileStatus.PENDING_REVIEW;
    }

    public void updateBasicInformation(
            String farmName,
            String representativeName,
            String contactNumber,
            String farmAddress,
            ChungbukCityCounty cityCounty,
            List<String> crops,
            String mainActivities,
            String businessRegistrationNumber,
            Integer farmAreaPyeong
    ) {
        if (!canUpdateBasicInformation()) {
            throw new IllegalStateException("심사 중이거나 비활성화된 농가 프로필은 수정할 수 없습니다.");
        }
        boolean ownershipIdentityChanged = ownershipIdentityDiffers(
                farmName,
                representativeName,
                farmAddress,
                cityCounty,
                businessRegistrationNumber,
                farmAreaPyeong
        );
        this.farmName = farmName;
        this.representativeName = representativeName;
        this.contactNumber = contactNumber;
        this.farmAddress = farmAddress;
        this.cityCounty = cityCounty;
        this.crops.clear();
        this.crops.addAll(crops);
        this.mainActivities = mainActivities;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.farmAreaPyeong = farmAreaPyeong;
        if (status == FarmProfileStatus.APPROVED && ownershipIdentityChanged) {
            status = FarmProfileStatus.DRAFT;
            reviewer = null;
            reviewedAt = null;
            rejectionReason = null;
        }
    }

    public void updateBasicInformation(
            String farmName,
            String representativeName,
            String contactNumber,
            String farmAddress,
            ChungbukCityCounty cityCounty,
            List<String> crops,
            String mainActivities,
            String businessRegistrationNumber
    ) {
        updateBasicInformation(farmName, representativeName, contactNumber,
                farmAddress, cityCounty, crops, mainActivities,
                businessRegistrationNumber, farmAreaPyeong == null ? 1 : farmAreaPyeong);
    }

    public void approveOwnership(User reviewer, Instant reviewedAt) {
        validateReview(reviewer, reviewedAt);
        status = FarmProfileStatus.APPROVED;
        this.reviewer = reviewer;
        this.reviewedAt = reviewedAt;
        rejectionReason = null;
    }

    public void rejectOwnership(
            User reviewer,
            Instant reviewedAt,
            String rejectionReason
    ) {
        validateReview(reviewer, reviewedAt);
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 필수입니다.");
        }
        status = FarmProfileStatus.REJECTED;
        this.reviewer = reviewer;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = rejectionReason;
    }

    private void validateReview(User reviewer, Instant reviewedAt) {
        if (status != FarmProfileStatus.PENDING_REVIEW) {
            throw new IllegalStateException("심사 대기 중인 농가 프로필만 처리할 수 있습니다.");
        }
        if (reviewer == null || reviewer.getUserType() != User.UserType.CENTER_ADMIN) {
            throw new IllegalArgumentException("담당자만 농가 프로필을 심사할 수 있습니다.");
        }
        if (reviewedAt == null) {
            throw new IllegalArgumentException("심사 시각은 필수입니다.");
        }
    }

    public void deactivate() {
        status = FarmProfileStatus.INACTIVE;
    }

    public boolean ownershipIdentityDiffers(
            String farmName,
            String representativeName,
            String farmAddress,
            ChungbukCityCounty cityCounty,
            String businessRegistrationNumber,
            Integer farmAreaPyeong
    ) {
        return !Objects.equals(this.farmName, farmName)
                || !Objects.equals(this.representativeName, representativeName)
                || !Objects.equals(this.farmAddress, farmAddress)
                || this.cityCounty != cityCounty
                || !Objects.equals(this.businessRegistrationNumber,
                businessRegistrationNumber)
                || !Objects.equals(this.farmAreaPyeong, farmAreaPyeong);
    }

    public enum FarmProfileStatus {
        DRAFT,
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        INACTIVE
    }
}
