package chungbuk.cityfarmerplus.urbanfarmer.profile.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
        name = "urban_farmer_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_urban_farmer_profiles_user",
                columnNames = "urban_farmer_user_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UrbanFarmerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "urban_farmer_user_id", nullable = false, updatable = false)
    private User urbanFarmer;

    @Column(name = "agricultural_business_registered", nullable = false)
    private boolean agriculturalBusinessRegistered;

    @Column(name = "experience_count", nullable = false)
    private int experienceCount;

    @Column(length = 1000)
    private String notes;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UrbanFarmerProfile create(
            User urbanFarmer,
            boolean agriculturalBusinessRegistered,
            int experienceCount,
            String notes
    ) {
        if (urbanFarmer.getUserType() != User.UserType.URBAN_FARMER) {
            throw new IllegalArgumentException("도시농부 사용자만 프로필을 만들 수 있습니다.");
        }
        validateExperienceCount(experienceCount);
        UrbanFarmerProfile profile = new UrbanFarmerProfile();
        profile.urbanFarmer = urbanFarmer;
        profile.agriculturalBusinessRegistered = agriculturalBusinessRegistered;
        profile.experienceCount = experienceCount;
        profile.notes = notes;
        return profile;
    }

    public void update(
            boolean agriculturalBusinessRegistered,
            int experienceCount,
            String notes
    ) {
        validateExperienceCount(experienceCount);
        this.agriculturalBusinessRegistered = agriculturalBusinessRegistered;
        this.experienceCount = experienceCount;
        this.notes = notes;
    }

    public void updateExperience(int experienceCount, String notes) {
        validateExperienceCount(experienceCount);
        this.experienceCount = experienceCount;
        this.notes = notes;
    }

    private static void validateExperienceCount(int experienceCount) {
        if (experienceCount < 0) {
            throw new IllegalArgumentException("활동 경험 횟수는 0 이상이어야 합니다.");
        }
    }
}
