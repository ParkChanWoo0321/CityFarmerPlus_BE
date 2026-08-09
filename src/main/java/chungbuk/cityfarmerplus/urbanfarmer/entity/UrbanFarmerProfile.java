package chungbuk.cityfarmerplus.urbanfarmer.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "urban_farmer_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrbanFarmerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 100)
    private String preferredRegion;

    @Column(nullable = false, length = 100)
    private String preferredDays;

    @Column(nullable = false, length = 100)
    private String preferredWorkType;

    @Column(nullable = false)
    private boolean canTravel;

    @Column(nullable = false)
    private int experienceCount;

    @Column(length = 500)
    private String introduction;

    @Column(nullable = false)
    private boolean farmBusinessRegistered;

    @Column(nullable = false)
    @Builder.Default
    private boolean eligibilityVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EducationStatus educationStatus = EducationStatus.NOT_COMPLETED;

    public enum EducationStatus {
        NOT_COMPLETED, APPLICABLE, CERTIFICATE_REGISTERED, COMPLETED
    }
}
