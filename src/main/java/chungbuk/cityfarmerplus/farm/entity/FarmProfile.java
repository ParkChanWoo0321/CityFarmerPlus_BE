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

@Entity
@Table(
        name = "farm_profiles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_farm_profiles_owner",
                        columnNames = "owner_user_id"
                )
        },
        indexes = @Index(
                name = "idx_farm_profiles_status",
                columnList = "status"
        )
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FarmProfileStatus status;

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
            String businessRegistrationNumber
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
        profile.status = FarmProfileStatus.DRAFT;
        return profile;
    }

    public enum FarmProfileStatus {
        DRAFT,
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        INACTIVE
    }
}
